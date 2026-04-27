// Ed25519 signature verification interface used by LumosConfig.
// Per Lumos Crypto Spec v1.0 §2 (long-term identity / config-signing keys).

import Foundation
import CryptoKit

public protocol Ed25519Verifier {
    /// Verify `signature` over `message` using the key registered for `keyId`.
    /// Returns true on valid match. Returns false (no throw) on mismatch.
    /// Throws if `keyId` is unknown — caller must treat as fail-closed.
    func verify(message: Data, signature: Data, keyId: String) throws -> Bool
}

public enum VerifierError: Error, Equatable {
    case unknownKeyId(String)
    case malformedKey
    case malformedSignature
}

public final class CryptoKitEd25519Verifier: Ed25519Verifier {

    private let trustAnchors: [String: Curve25519.Signing.PublicKey]

    /// Build with raw 32-byte trust-anchor public keys. App ships these embedded.
    public init(trustAnchors: [String: Data]) throws {
        var built: [String: Curve25519.Signing.PublicKey] = [:]
        for (id, raw) in trustAnchors {
            do { built[id] = try Curve25519.Signing.PublicKey(rawRepresentation: raw) }
            catch { throw VerifierError.malformedKey }
        }
        self.trustAnchors = built
    }

    public func verify(message: Data, signature: Data, keyId: String) throws -> Bool {
        guard let pub = trustAnchors[keyId] else { throw VerifierError.unknownKeyId(keyId) }
        if signature.count != 64 { return false }
        return pub.isValidSignature(signature, for: message)
    }
}

/// Test/dev-only verifier that accepts everything. NEVER use in production.
public final class AlwaysAcceptVerifier: Ed25519Verifier {
    public init() {}
    public func verify(message: Data, signature: Data, keyId: String) throws -> Bool { return true }
}
