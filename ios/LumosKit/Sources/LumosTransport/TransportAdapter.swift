// LumosTransport — radio-agnostic transport abstraction.
// Per Lumos SAS / Protocol & Interop Contract v1.0 §2 ("transport envelope layer").

import Foundation
import LumosProtocol

public enum TransportEvent: Equatable, Sendable {
    case peerDiscovered(peerId: String, kind: TransportKind)
    case peerLost(peerId: String, kind: TransportKind)
    case linkUp(kind: TransportKind)
    case linkDown(kind: TransportKind, reason: String)
    case framed(bytes: Data, kind: TransportKind)
    case sendResult(messageId: String, success: Bool, retryable: Bool)
}

public protocol TransportAdapter: AnyObject, Sendable {
    var kind: TransportKind { get }
    /// Stream of asynchronous events from the radio layer. Unbounded; consumers must
    /// not block. Implementations should buffer with a bounded queue (per Crypto §5).
    var events: AsyncStream<TransportEvent> { get }
    func start() async throws
    func stop() async
    /// Send a single envelope's bytes to a peer. Implementations chunk if the radio
    /// MTU is smaller than the envelope. Returns when the bytes have been handed
    /// off to the radio queue (not when delivered).
    func send(envelope bytes: Data, to peerId: String, messageId: String) async throws
}

/// Test/dev fake transport. Wires sender→receiver in-process. Used by interop simulator.
public final class LoopbackTransport: TransportAdapter, @unchecked Sendable {
    public let kind: TransportKind
    public let events: AsyncStream<TransportEvent>
    private let continuation: AsyncStream<TransportEvent>.Continuation
    public weak var peer: LoopbackTransport?

    public init(kind: TransportKind) {
        self.kind = kind
        var c: AsyncStream<TransportEvent>.Continuation!
        self.events = AsyncStream { c = $0 }
        self.continuation = c
    }

    public func start() async throws { continuation.yield(.linkUp(kind: kind)) }
    public func stop() async { continuation.yield(.linkDown(kind: kind, reason: "stop")) }
    public func send(envelope bytes: Data, to peerId: String, messageId: String) async throws {
        guard let peer = peer else {
            continuation.yield(.sendResult(messageId: messageId, success: false, retryable: true))
            return
        }
        peer.continuation.yield(.framed(bytes: bytes, kind: kind))
        continuation.yield(.sendResult(messageId: messageId, success: true, retryable: false))
    }
}
