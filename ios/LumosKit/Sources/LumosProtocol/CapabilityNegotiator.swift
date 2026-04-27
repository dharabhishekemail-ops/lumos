// Capability negotiation between local Hello and remote Hello (or Hello+Ack).
// Per Lumos Crypto Protocol Spec v1.0 §3 and Protocol & Interop Contract v1.0 §2.

import Foundation

public struct LocalCapabilities: Sendable {
    public let protocolVersions: [Int]
    public let transports: [TransportKind]
    public let aeadSuites: [AeadSuite]
    public let kdfSuites: [KdfSuite]
    public let curves: [CurveSuite]
    public let features: [FeatureFlag]

    public init(protocolVersions: [Int], transports: [TransportKind], aeadSuites: [AeadSuite],
                kdfSuites: [KdfSuite], curves: [CurveSuite], features: [FeatureFlag]) {
        self.protocolVersions = protocolVersions; self.transports = transports
        self.aeadSuites = aeadSuites; self.kdfSuites = kdfSuites
        self.curves = curves; self.features = features
    }

    /// Reasonable production defaults.
    public static let defaultLocal = LocalCapabilities(
        protocolVersions: [1],
        transports: [.ble, .wifi, .qr],
        aeadSuites: [.chacha20Poly1305, .aes256Gcm],
        kdfSuites: [.hkdfSha256],
        curves: [.x25519],
        features: [.chatText, .chatMediaPhoto, .voucherQr]
    )
}

public enum NegotiationResult: Sendable {
    case ok(HelloAckPayload)
    case incompatibleVersion
    case noCommonTransport
    case noCommonAead
    case noCommonKdf
    case noCommonCurve
}

public enum CapabilityNegotiator {

    /// Negotiate against a remote Hello message. Returns the HelloAck this side should reply with,
    /// or an explicit failure reason (which the caller turns into an ERROR/GOODBYE).
    public static func negotiate(local: LocalCapabilities, remoteHello: HelloPayload) -> NegotiationResult {
        // 1. Pick highest mutually supported protocol version.
        let mutualV = Set(local.protocolVersions).intersection(Set(remoteHello.protocolVersions))
        guard let v = mutualV.max() else { return .incompatibleVersion }

        // 2. Transport: pick first local-preferred transport that the remote also offers.
        guard let transport = local.transports.first(where: { remoteHello.transports.contains($0) }) else {
            return .noCommonTransport
        }
        // 3. AEAD: prefer local's order.
        guard let aead = local.aeadSuites.first(where: { remoteHello.aeadSuites.contains($0) }) else {
            return .noCommonAead
        }
        // 4. KDF
        guard let kdf = local.kdfSuites.first(where: { remoteHello.kdfSuites.contains($0) }) else {
            return .noCommonKdf
        }
        // 5. Curve
        guard let curve = local.curves.first(where: { remoteHello.curves.contains($0) }) else {
            return .noCommonCurve
        }
        // 6. Features: intersection, in local's preferred order.
        let features = local.features.filter { remoteHello.features.contains($0) }

        return .ok(HelloAckPayload(
            selectedProtocolVersion: v,
            selectedTransport: transport,
            selectedAead: aead,
            selectedKdf: kdf,
            selectedCurve: curve,
            featuresAccepted: features
        ))
    }
}
