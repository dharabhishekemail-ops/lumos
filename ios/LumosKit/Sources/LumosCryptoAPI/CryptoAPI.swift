// LumosCryptoAPI — cryptographic interfaces and concrete CryptoKit-backed
// implementations. Per Lumos Crypto Protocol Spec v1.0 §2.
//
// Algorithm choices (locked):
//   - X25519 ephemeral key agreement
//   - HKDF-SHA256
//   - ChaCha20-Poly1305 (or AES-256-GCM if peer requests)
//   - SHA-256 hash, OS RNG
// Identity keys live in iOS Keychain; this module exposes only the abstract API.

import Foundation
import CryptoKit

// MARK: - Errors

public enum CryptoFault: Error, Equatable {
    case authTagFail
    case keyMaterialUnavailable
    case unsupportedAlgorithm(String)
    case rng
    case sizeOutOfRange
}

// MARK: - X25519

public protocol X25519KeyAgreement {
    func generateEphemeral() throws -> (publicKeyRaw: Data, privateHandle: Any)
    func deriveShared(localPrivate: Any, remotePublicKeyRaw: Data) throws -> Data
}

public final class CryptoKitX25519: X25519KeyAgreement {
    public init() {}
    public func generateEphemeral() throws -> (publicKeyRaw: Data, privateHandle: Any) {
        let priv = Curve25519.KeyAgreement.PrivateKey()
        return (priv.publicKey.rawRepresentation, priv)
    }
    public func deriveShared(localPrivate: Any, remotePublicKeyRaw: Data) throws -> Data {
        guard let priv = localPrivate as? Curve25519.KeyAgreement.PrivateKey else {
            throw CryptoFault.keyMaterialUnavailable
        }
        let pub = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: remotePublicKeyRaw)
        let secret = try priv.sharedSecretFromKeyAgreement(with: pub)
        return secret.withUnsafeBytes { Data($0) }
    }
}

// MARK: - HKDF

public protocol HkdfSha256 {
    func derive(ikm: Data, salt: Data, info: Data, length: Int) throws -> Data
}

public final class CryptoKitHkdf: HkdfSha256 {
    public init() {}
    public func derive(ikm: Data, salt: Data, info: Data, length: Int) throws -> Data {
        if length <= 0 || length > 1024 { throw CryptoFault.sizeOutOfRange }
        let key = SymmetricKey(data: ikm)
        let derived = HKDF<SHA256>.deriveKey(inputKeyMaterial: key, salt: salt, info: info, outputByteCount: length)
        return derived.withUnsafeBytes { Data($0) }
    }
}

// MARK: - AEAD

public protocol AeadCipher {
    var suite: AeadSuite { get }
    func seal(key: Data, nonce: Data, plaintext: Data, ad: Data) throws -> Data
    func open(key: Data, nonce: Data, ciphertext: Data, ad: Data) throws -> Data
}

public final class ChaCha20Poly1305Aead: AeadCipher {
    public let suite: AeadSuite = .chacha20Poly1305
    public init() {}
    public func seal(key: Data, nonce: Data, plaintext: Data, ad: Data) throws -> Data {
        let symKey = SymmetricKey(data: key)
        let n = try ChaChaPoly.Nonce(data: nonce)
        let box = try ChaChaPoly.seal(plaintext, using: symKey, nonce: n, authenticating: ad)
        // Return nonce-less combined: ciphertext || tag (the protocol carries nonce separately)
        return box.ciphertext + box.tag
    }
    public func open(key: Data, nonce: Data, ciphertext: Data, ad: Data) throws -> Data {
        if ciphertext.count < 16 { throw CryptoFault.authTagFail }
        let symKey = SymmetricKey(data: key)
        let tag = ciphertext.suffix(16)
        let body = ciphertext.prefix(ciphertext.count - 16)
        let n = try ChaChaPoly.Nonce(data: nonce)
        let box = try ChaChaPoly.SealedBox(nonce: n, ciphertext: body, tag: tag)
        do { return try ChaChaPoly.open(box, using: symKey, authenticating: ad) }
        catch { throw CryptoFault.authTagFail }
    }
}

public final class Aes256GcmAead: AeadCipher {
    public let suite: AeadSuite = .aes256Gcm
    public init() {}
    public func seal(key: Data, nonce: Data, plaintext: Data, ad: Data) throws -> Data {
        let symKey = SymmetricKey(data: key)
        let n = try AES.GCM.Nonce(data: nonce)
        let box = try AES.GCM.seal(plaintext, using: symKey, nonce: n, authenticating: ad)
        return box.ciphertext + box.tag
    }
    public func open(key: Data, nonce: Data, ciphertext: Data, ad: Data) throws -> Data {
        if ciphertext.count < 16 { throw CryptoFault.authTagFail }
        let symKey = SymmetricKey(data: key)
        let tag = ciphertext.suffix(16)
        let body = ciphertext.prefix(ciphertext.count - 16)
        let n = try AES.GCM.Nonce(data: nonce)
        let box = try AES.GCM.SealedBox(nonce: n, ciphertext: body, tag: tag)
        do { return try AES.GCM.open(box, using: symKey, authenticating: ad) }
        catch { throw CryptoFault.authTagFail }
    }
}

// MARK: - Random

public protocol SecureRandom {
    func bytes(_ count: Int) throws -> Data
}

public final class OsSecureRandom: SecureRandom {
    public init() {}
    public func bytes(_ count: Int) throws -> Data {
        if count <= 0 || count > 4096 { throw CryptoFault.sizeOutOfRange }
        var data = Data(count: count)
        let result = data.withUnsafeMutableBytes { ptr -> Int32 in
            guard let base = ptr.baseAddress else { return errSecAllocate }
            return SecRandomCopyBytes(kSecRandomDefault, count, base)
        }
        if result != errSecSuccess { throw CryptoFault.rng }
        return data
    }
}

// MARK: - Facade used by the session orchestrator

public final class CryptoFacade {
    public let kex: X25519KeyAgreement
    public let kdf: HkdfSha256
    public let rng: SecureRandom
    public let aeads: [AeadSuite: AeadCipher]

    public init(kex: X25519KeyAgreement = CryptoKitX25519(),
                kdf: HkdfSha256 = CryptoKitHkdf(),
                rng: SecureRandom = OsSecureRandom(),
                aeads: [AeadSuite: AeadCipher] = [
                    .chacha20Poly1305: ChaCha20Poly1305Aead(),
                    .aes256Gcm: Aes256GcmAead(),
                ]) {
        self.kex = kex; self.kdf = kdf; self.rng = rng; self.aeads = aeads
    }

    public func aead(_ suite: AeadSuite) throws -> AeadCipher {
        guard let a = aeads[suite] else { throw CryptoFault.unsupportedAlgorithm(suite.rawValue) }
        return a
    }
}
