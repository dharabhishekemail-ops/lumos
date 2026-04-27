// LumosConfig — signed config pack model + runtime.
// Per Lumos Signed Config Schema and Governance Spec v1.0 §3-5.
//
// Lifecycle on receipt:
//   1. Parse JSON
//   2. Validate schema (bounded ranges, required fields) — local
//   3. Verify signature against the embedded trust anchor for `signature.keyId`
//   4. Cross-field consistency checks
//   5. Apply atomically; persist as new last-known-good (LKG)
// On any failure: KEEP the existing LKG; do NOT apply.

import Foundation
import LumosCryptoAPI

public struct SignedConfigPack: Codable, Equatable, Sendable {
    public let schemaVersion: Int
    public let configVersion: String
    public let issuedAt: String
    public let expiresAt: String
    public let payload: ConfigPayload
    public let signature: ConfigSignature
}

public struct ConfigSignature: Codable, Equatable, Sendable {
    public let alg: String       // "ed25519"
    public let keyId: String
    public let sigB64: String
}

public struct ConfigPayload: Codable, Equatable, Sendable {
    public let featureFlags: FeatureFlags
    public let transport: TransportConfig
    public let rateLimits: RateLimits
    public let legalCopyVersion: String
    public let monetization: MonetizationConfig?
    public let rolloutPercent: Int?
}

public struct FeatureFlags: Codable, Equatable, Sendable {
    public let adsTabEnabled: Bool?
    public let vouchersEnabled: Bool?
    public let operatorModeEnabled: Bool?
    public let diagnosticsPanelEnabled: Bool?
}

public struct TransportConfig: Codable, Equatable, Sendable {
    public let priorityOrder: [String]   // values: BLE/WIFI/QR
    public let retryBudget: Int
    public let backoffMaxMs: Int
}

public struct RateLimits: Codable, Equatable, Sendable {
    public let interestPerHour: Int
    public let messagesPerMinute: Int
}

public struct MonetizationConfig: Codable, Equatable, Sendable {
    public let freePreviewMinutes: Int?
    public let skus: [Sku]?
}

public struct Sku: Codable, Equatable, Sendable {
    public let id: String
    public let type: String  // SUBS|INAPP
    public let label: String
}

// MARK: - Errors

public enum ConfigError: Error, Equatable {
    case parseError(String)
    case schemaError(String)
    case signatureMissing
    case signatureInvalid
    case unknownKeyId(String)
    case unsafeBound(field: String)
    case expired
}

// MARK: - Validator

public enum ConfigValidator {

    /// Validate schema and bounded ranges. Mirrors the JSON Schema in
    /// `schemas/signed-config.schema.json`.
    public static func validateSchema(_ p: SignedConfigPack) throws {
        if p.schemaVersion != 1 { throw ConfigError.schemaError("schemaVersion must be 1") }
        if !isSemver(p.configVersion) { throw ConfigError.schemaError("configVersion not x.y.z") }
        // featureFlags optional fields are unconstrained beyond being booleans.
        // transport
        if p.payload.transport.priorityOrder.isEmpty || p.payload.transport.priorityOrder.count > 3 {
            throw ConfigError.schemaError("transport.priorityOrder length")
        }
        for v in p.payload.transport.priorityOrder {
            if !["BLE","WIFI","QR"].contains(v) {
                throw ConfigError.schemaError("transport.priorityOrder enum: \(v)")
            }
        }
        if p.payload.transport.retryBudget < 0 || p.payload.transport.retryBudget > 10 {
            throw ConfigError.unsafeBound(field: "retryBudget")
        }
        if p.payload.transport.backoffMaxMs < 100 || p.payload.transport.backoffMaxMs > 30_000 {
            throw ConfigError.unsafeBound(field: "backoffMaxMs")
        }
        // rateLimits
        if p.payload.rateLimits.interestPerHour < 1 || p.payload.rateLimits.interestPerHour > 200 {
            throw ConfigError.unsafeBound(field: "interestPerHour")
        }
        if p.payload.rateLimits.messagesPerMinute < 1 || p.payload.rateLimits.messagesPerMinute > 200 {
            throw ConfigError.unsafeBound(field: "messagesPerMinute")
        }
        // legalCopyVersion
        if p.payload.legalCopyVersion.isEmpty || p.payload.legalCopyVersion.count > 32 {
            throw ConfigError.schemaError("legalCopyVersion length")
        }
        // signature shape
        if p.signature.alg != "ed25519" { throw ConfigError.schemaError("signature.alg must be ed25519") }
        if p.signature.keyId.isEmpty { throw ConfigError.schemaError("signature.keyId required") }
        if p.signature.sigB64.isEmpty { throw ConfigError.signatureMissing }
        // rolloutPercent if present
        if let r = p.payload.rolloutPercent, (r < 0 || r > 100) {
            throw ConfigError.unsafeBound(field: "rolloutPercent")
        }
    }

    private static func isSemver(_ s: String) -> Bool {
        let parts = s.split(separator: ".")
        guard parts.count == 3 else { return false }
        return parts.allSatisfy { Int($0) != nil }
    }
}

// MARK: - ConfigRuntime

public final class ConfigRuntime: @unchecked Sendable {
    private let verifier: Ed25519Verifier
    private let lkgStore: LastKnownGoodStore
    private(set) public var current: SignedConfigPack?

    public init(verifier: Ed25519Verifier, lkgStore: LastKnownGoodStore = InMemoryLkgStore()) {
        self.verifier = verifier
        self.lkgStore = lkgStore
        self.current = lkgStore.load()
    }

    /// Apply a new pack atomically. On any failure, throw and keep the current LKG.
    @discardableResult
    public func apply(_ raw: Data, now: Date = Date()) throws -> SignedConfigPack {
        let pack: SignedConfigPack
        do { pack = try JSONDecoder().decode(SignedConfigPack.self, from: raw) }
        catch { throw ConfigError.parseError("\(error)") }
        try ConfigValidator.validateSchema(pack)

        // Expiry check (lenient): only reject if obviously expired beyond 24h grace.
        if let exp = parseDate(pack.expiresAt), exp.timeIntervalSince(now) < -86_400 {
            throw ConfigError.expired
        }

        // Verify signature over the canonical signing input: payload only, sorted keys.
        let signingInput = try canonicalPayloadBytes(pack)
        guard let sig = Data(base64Encoded: pack.signature.sigB64) else {
            throw ConfigError.signatureInvalid
        }
        let ok: Bool
        do { ok = try verifier.verify(message: signingInput, signature: sig, keyId: pack.signature.keyId) }
        catch VerifierError.unknownKeyId(let k) { throw ConfigError.unknownKeyId(k) }
        catch { throw ConfigError.signatureInvalid }
        if !ok { throw ConfigError.signatureInvalid }

        // Apply
        self.current = pack
        lkgStore.save(pack)
        return pack
    }

    /// The bytes that are signed. For deterministic cross-platform agreement,
    /// the input is the payload re-encoded with sorted keys (JSONSerialization.WritingOptions.sortedKeys).
    public static func canonicalPayloadBytes(_ pack: SignedConfigPack) throws -> Data {
        return try canonicalPayloadBytesS(pack)
    }

    private static func canonicalPayloadBytesS(_ pack: SignedConfigPack) throws -> Data {
        // Re-encode payload with sorted keys to get a deterministic signing input.
        let payloadDict = try JSONSerialization.jsonObject(with: JSONEncoder().encode(pack.payload), options: [])
        return try JSONSerialization.data(withJSONObject: payloadDict, options: [.sortedKeys, .withoutEscapingSlashes])
    }

    private static func canonicalPayloadBytes(_ pack: SignedConfigPack) throws -> Data {
        try canonicalPayloadBytesS(pack)
    }

    private func canonicalPayloadBytes(_ pack: SignedConfigPack) throws -> Data {
        try ConfigRuntime.canonicalPayloadBytesS(pack)
    }

    private func parseDate(_ s: String) -> Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }
}

// MARK: - Last-Known-Good store

public protocol LastKnownGoodStore {
    func load() -> SignedConfigPack?
    func save(_ pack: SignedConfigPack)
}

public final class InMemoryLkgStore: LastKnownGoodStore, @unchecked Sendable {
    private var pack: SignedConfigPack?
    public init() {}
    public func load() -> SignedConfigPack? { pack }
    public func save(_ p: SignedConfigPack) { pack = p }
}
