// LumosSession — session state machine + dedupe + replay window + retry policy.
// Per Lumos Protocol & Interop Contract v1.0 §5 (replay window: 64-message
// sliding window, indexed by messageId), and Crypto Spec v1.0 §5 (retry budget
// applies to retry-eligible outbound sends only).

import Foundation
import LumosCommon
import LumosProtocol
import LumosCryptoAPI
import LumosTransport

// MARK: - Errors and policy

public struct RetryPolicy: Equatable, Sendable {
    public let maxRetries: Int
    public let initialBackoffMs: Int
    public let backoffMaxMs: Int
    public init(maxRetries: Int = 5, initialBackoffMs: Int = 200, backoffMaxMs: Int = 8_000) {
        self.maxRetries = maxRetries
        self.initialBackoffMs = initialBackoffMs
        self.backoffMaxMs = backoffMaxMs
    }
    public func backoff(forAttempt n: Int) -> Int {
        // Exponential capped: initial * 2^(n-1), clamped to backoffMaxMs.
        let raw = initialBackoffMs * (1 << max(0, min(n - 1, 16)))
        return min(raw, backoffMaxMs)
    }
}

// MARK: - Replay window (sliding 64 messages)

public struct DedupeWindow: Sendable {
    private(set) public var seen: [String]
    public let capacity: Int
    public init(capacity: Int = 64) {
        precondition(capacity > 0)
        self.capacity = capacity
        self.seen = []
        self.seen.reserveCapacity(capacity)
    }
    /// Returns true if this messageId is fresh; false if it's a replay.
    public mutating func acceptIfFresh(_ messageId: String) -> Bool {
        if seen.contains(messageId) { return false }
        if seen.count == capacity { seen.removeFirst() }
        seen.append(messageId)
        return true
    }
}

// MARK: - State

public enum SessionState: Equatable, Sendable {
    case idle
    case helloSent
    case helloAckReceived
    case established
    case migrating
    case closed
}

public enum SessionEvent: Equatable, Sendable {
    case stateChanged(SessionState)
    case received(TypedMessage)
    case sent(messageId: String)
    case sendFailed(messageId: String, retryable: Bool)
    case errorRaised(ErrorPayload)
    case replayDropped(messageId: String)
    case sizeLimitDropped(bytes: Int)
}

// MARK: - Reducer (pure, testable)

public enum SessionReducer {

    /// Reduce a state + event into a new state. Pure — no I/O.
    public static func reduce(_ state: SessionState, msg: TypedMessage) -> SessionState {
        switch (state, msg.payload) {
        case (.idle, .hello): return .helloSent
        case (.helloSent, .helloAck): return .helloAckReceived
        case (.helloAckReceived, .interestRequest), (.helloAckReceived, .matchEstablished): return .established
        case (.established, .transportMigrate): return .migrating
        case (.migrating, .helloAck): return .established
        case (_, .goodbye): return .closed
        default: return state
        }
    }
}

// MARK: - Orchestrator (actor — concurrency safe)

public actor SessionOrchestrator {

    public let sessionId: String
    public private(set) var state: SessionState = .idle
    public let transport: TransportAdapter
    public let retryPolicy: RetryPolicy
    private var dedupe: DedupeWindow
    private var lastSeq: Int64 = -1

    private let eventStream: AsyncStream<SessionEvent>
    private let eventContinuation: AsyncStream<SessionEvent>.Continuation
    public nonisolated let events: AsyncStream<SessionEvent>

    public init(sessionId: String, transport: TransportAdapter,
                retryPolicy: RetryPolicy = RetryPolicy(),
                dedupeCapacity: Int = 64) {
        self.sessionId = sessionId
        self.transport = transport
        self.retryPolicy = retryPolicy
        self.dedupe = DedupeWindow(capacity: dedupeCapacity)
        var c: AsyncStream<SessionEvent>.Continuation!
        self.eventStream = AsyncStream { c = $0 }
        self.eventContinuation = c
        self.events = self.eventStream
    }

    /// Drive an inbound raw envelope. Validates, dedupes, advances state, emits an event.
    public func ingest(_ raw: Data) async {
        if raw.count > ProtocolLimits.maxEnvelopeBytes {
            eventContinuation.yield(.sizeLimitDropped(bytes: raw.count))
            return
        }
        let msg: TypedMessage
        do { msg = try ProtocolCodec.decode(raw) }
        catch {
            eventContinuation.yield(.errorRaised(ErrorPayload(code: .parseError, retryable: false, message: "\(error)")))
            return
        }
        if msg.envelope.sessionId != self.sessionId {
            eventContinuation.yield(.errorRaised(ErrorPayload(code: .sessionUnknown, retryable: false)))
            return
        }
        if !dedupe.acceptIfFresh(msg.envelope.messageId) {
            eventContinuation.yield(.replayDropped(messageId: msg.envelope.messageId))
            return
        }
        state = SessionReducer.reduce(state, msg: msg)
        eventContinuation.yield(.stateChanged(state))
        eventContinuation.yield(.received(msg))
    }

    /// Send an outbound message with retry. Returns when the message has been
    /// acknowledged by the transport adapter, OR when the retry budget is exhausted.
    @discardableResult
    public func send(_ msg: TypedMessage, to peerId: String) async -> Bool {
        precondition(msg.envelope.sessionId == self.sessionId, "envelope sessionId mismatch")
        let raw: Data
        do { raw = try ProtocolCodec.encode(msg) }
        catch {
            eventContinuation.yield(.errorRaised(ErrorPayload(code: .schemaError, retryable: false, message: "\(error)")))
            return false
        }
        var attempt = 0
        while attempt <= retryPolicy.maxRetries {
            do {
                try await transport.send(envelope: raw, to: peerId, messageId: msg.envelope.messageId)
                eventContinuation.yield(.sent(messageId: msg.envelope.messageId))
                state = SessionReducer.reduce(state, msg: msg)
                eventContinuation.yield(.stateChanged(state))
                return true
            } catch {
                attempt += 1
                if attempt > retryPolicy.maxRetries {
                    eventContinuation.yield(.sendFailed(messageId: msg.envelope.messageId, retryable: false))
                    return false
                }
                let backoff = retryPolicy.backoff(forAttempt: attempt)
                try? await Task.sleep(nanoseconds: UInt64(backoff) * 1_000_000)
            }
        }
        return false
    }

    /// Force the session into the closed state (used on Goodbye / fatal error).
    public func close() {
        state = .closed
        eventContinuation.yield(.stateChanged(state))
        eventContinuation.finish()
    }
}
