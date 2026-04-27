// LumosProtocol — canonical wire types. Byte-identical to Android Kotlin and
// to the Python reference implementation in tools/canonical_codec.py.
// Per Lumos Protocol & Interop Contract v1.0 §3.

import Foundation

// MARK: - Enums (string-valued)

public enum MsgType: String, Codable, CaseIterable, Sendable {
    case hello = "HELLO"
    case helloAck = "HELLO_ACK"
    case interestRequest = "INTEREST_REQUEST"
    case interestResponse = "INTEREST_RESPONSE"
    case matchEstablished = "MATCH_ESTABLISHED"
    case chatText = "CHAT_TEXT"
    case chatMediaChunk = "CHAT_MEDIA_CHUNK"
    case chatMediaAck = "CHAT_MEDIA_ACK"
    case heartbeat = "HEARTBEAT"
    case errorMsg = "ERROR"
    case transportMigrate = "TRANSPORT_MIGRATE"
    case goodbye = "GOODBYE"
}

public enum TransportKind: String, Codable, CaseIterable, Sendable {
    case ble = "BLE"
    case wifi = "WIFI"
    case qr = "QR"
}

public enum PayloadEncoding: String, Codable, Sendable {
    case json = "JSON"
}

public enum AeadSuite: String, Codable, CaseIterable, Sendable {
    case chacha20Poly1305 = "CHACHA20_POLY1305"
    case aes256Gcm = "AES_256_GCM"
}

public enum KdfSuite: String, Codable, CaseIterable, Sendable {
    case hkdfSha256 = "HKDF_SHA256"
}

public enum CurveSuite: String, Codable, CaseIterable, Sendable {
    case x25519 = "X25519"
}

public enum FeatureFlag: String, Codable, CaseIterable, Sendable {
    case chatText = "CHAT_TEXT"
    case chatMediaPhoto = "CHAT_MEDIA_PHOTO"
    case voucherQr = "VOUCHER_QR"
}

public enum InterestIntent: String, Codable, CaseIterable, Sendable {
    case dating, chat, networking
}

public enum ErrorCode: String, Codable, CaseIterable, Sendable {
    case parseError = "PARSE_ERROR"
    case schemaError = "SCHEMA_ERROR"
    case authTagFail = "AUTH_TAG_FAIL"
    case sessionUnknown = "SESSION_UNKNOWN"
    case replayDetected = "REPLAY_DETECTED"
    case rateLimited = "RATE_LIMITED"
    case incompatibleVersion = "INCOMPATIBLE_VERSION"
    case transportFailed = "TRANSPORT_FAILED"
    case internalError = "INTERNAL"
}

public enum GoodbyeReason: String, Codable, CaseIterable, Sendable {
    case userInitiated = "USER_INITIATED"
    case sessionTimeout = "SESSION_TIMEOUT"
    case fatalError = "FATAL_ERROR"
    case peerBlocked = "PEER_BLOCKED"
}

// MARK: - Envelope (canonical, single shape)

public struct Envelope: Codable, Equatable, Sendable {
    public let v: Int
    public let msgType: MsgType
    public let sessionId: String
    public let messageId: String
    public let sentAtMs: Int64
    public let transport: TransportKind
    public let payloadEncoding: PayloadEncoding

    public init(v: Int = 1, msgType: MsgType, sessionId: String, messageId: String,
                sentAtMs: Int64, transport: TransportKind, payloadEncoding: PayloadEncoding = .json) {
        self.v = v
        self.msgType = msgType
        self.sessionId = sessionId
        self.messageId = messageId
        self.sentAtMs = sentAtMs
        self.transport = transport
        self.payloadEncoding = payloadEncoding
    }
}

// MARK: - Payloads

public struct HelloPayload: Codable, Equatable, Sendable {
    public let protocolVersions: [Int]
    public let transports: [TransportKind]
    public let aeadSuites: [AeadSuite]
    public let kdfSuites: [KdfSuite]
    public let curves: [CurveSuite]
    public let features: [FeatureFlag]
    public let nonce: String

    public init(protocolVersions: [Int], transports: [TransportKind], aeadSuites: [AeadSuite],
                kdfSuites: [KdfSuite], curves: [CurveSuite], features: [FeatureFlag], nonce: String) {
        self.protocolVersions = protocolVersions; self.transports = transports
        self.aeadSuites = aeadSuites; self.kdfSuites = kdfSuites
        self.curves = curves; self.features = features; self.nonce = nonce
    }
}

public struct HelloAckPayload: Codable, Equatable, Sendable {
    public let selectedProtocolVersion: Int
    public let selectedTransport: TransportKind
    public let selectedAead: AeadSuite
    public let selectedKdf: KdfSuite
    public let selectedCurve: CurveSuite
    public let featuresAccepted: [FeatureFlag]

    public init(selectedProtocolVersion: Int, selectedTransport: TransportKind,
                selectedAead: AeadSuite, selectedKdf: KdfSuite, selectedCurve: CurveSuite,
                featuresAccepted: [FeatureFlag]) {
        self.selectedProtocolVersion = selectedProtocolVersion
        self.selectedTransport = selectedTransport
        self.selectedAead = selectedAead; self.selectedKdf = selectedKdf
        self.selectedCurve = selectedCurve; self.featuresAccepted = featuresAccepted
    }
}

public struct PreviewProfile: Codable, Equatable, Sendable {
    public let alias: String
    public let tags: [String]
    public let intent: InterestIntent
    public init(alias: String, tags: [String], intent: InterestIntent) {
        self.alias = alias; self.tags = tags; self.intent = intent
    }
}

public struct InterestRequestPayload: Codable, Equatable, Sendable {
    public let previewProfile: PreviewProfile
    public let interestToken: String
    public init(previewProfile: PreviewProfile, interestToken: String) {
        self.previewProfile = previewProfile; self.interestToken = interestToken
    }
}

public struct InterestResponsePayload: Codable, Equatable, Sendable {
    public let accepted: Bool
    public let reason: String?
    public init(accepted: Bool, reason: String? = nil) {
        self.accepted = accepted; self.reason = reason
    }
}

public struct MatchEstablishedPayload: Codable, Equatable, Sendable {
    public let matchId: String
    public init(matchId: String) { self.matchId = matchId }
}

public struct ChatTextPayload: Codable, Equatable, Sendable {
    public let ciphertextB64: String
    public let aadHashHex: String
    public let ratchetIndex: Int64
    public init(ciphertextB64: String, aadHashHex: String, ratchetIndex: Int64) {
        self.ciphertextB64 = ciphertextB64; self.aadHashHex = aadHashHex; self.ratchetIndex = ratchetIndex
    }
}

public struct ChatMediaChunkPayload: Codable, Equatable, Sendable {
    public let transferId: String
    public let chunkIndex: Int
    public let isLast: Bool
    public let ciphertextB64: String
    public let sha256Hex: String
    public init(transferId: String, chunkIndex: Int, isLast: Bool, ciphertextB64: String, sha256Hex: String) {
        self.transferId = transferId; self.chunkIndex = chunkIndex; self.isLast = isLast
        self.ciphertextB64 = ciphertextB64; self.sha256Hex = sha256Hex
    }
}

public struct ChatMediaAckPayload: Codable, Equatable, Sendable {
    public let transferId: String
    public let highestContiguousChunk: Int
    public init(transferId: String, highestContiguousChunk: Int) {
        self.transferId = transferId; self.highestContiguousChunk = highestContiguousChunk
    }
}

public struct HeartbeatPayload: Codable, Equatable, Sendable {
    public let seq: Int64
    public init(seq: Int64) { self.seq = seq }
}

public struct ErrorPayload: Codable, Equatable, Sendable {
    public let code: ErrorCode
    public let retryable: Bool
    public let message: String?
    public init(code: ErrorCode, retryable: Bool, message: String? = nil) {
        self.code = code; self.retryable = retryable; self.message = message
    }
}

public struct TransportMigratePayload: Codable, Equatable, Sendable {
    public let proposed: [TransportKind]
    public init(proposed: [TransportKind]) { self.proposed = proposed }
}

public struct GoodbyePayload: Codable, Equatable, Sendable {
    public let reason: GoodbyeReason
    public init(reason: GoodbyeReason) { self.reason = reason }
}

// MARK: - Typed message (envelope + typed payload)

public enum TypedPayload: Equatable, Sendable {
    case hello(HelloPayload)
    case helloAck(HelloAckPayload)
    case interestRequest(InterestRequestPayload)
    case interestResponse(InterestResponsePayload)
    case matchEstablished(MatchEstablishedPayload)
    case chatText(ChatTextPayload)
    case chatMediaChunk(ChatMediaChunkPayload)
    case chatMediaAck(ChatMediaAckPayload)
    case heartbeat(HeartbeatPayload)
    case errorMsg(ErrorPayload)
    case transportMigrate(TransportMigratePayload)
    case goodbye(GoodbyePayload)

    public var msgType: MsgType {
        switch self {
        case .hello: return .hello
        case .helloAck: return .helloAck
        case .interestRequest: return .interestRequest
        case .interestResponse: return .interestResponse
        case .matchEstablished: return .matchEstablished
        case .chatText: return .chatText
        case .chatMediaChunk: return .chatMediaChunk
        case .chatMediaAck: return .chatMediaAck
        case .heartbeat: return .heartbeat
        case .errorMsg: return .errorMsg
        case .transportMigrate: return .transportMigrate
        case .goodbye: return .goodbye
        }
    }
}

public struct TypedMessage: Equatable, Sendable {
    public let envelope: Envelope
    public let payload: TypedPayload
    public init(envelope: Envelope, payload: TypedPayload) {
        precondition(envelope.msgType == payload.msgType, "envelope.msgType must match payload type")
        self.envelope = envelope; self.payload = payload
    }
}
