import XCTest
@testable import LumosCryptoAPI
import CryptoKit

final class CryptoAPITests: XCTestCase {

    func testHkdfDeriveLength() throws {
        let kdf = CryptoKitHkdf()
        let ikm = Data(repeating: 0xAB, count: 32)
        let salt = Data(repeating: 0xCD, count: 16)
        let info = Data("lumos.session.v1".utf8)
        let out = try kdf.derive(ikm: ikm, salt: salt, info: info, length: 64)
        XCTAssertEqual(out.count, 64)
    }

    func testChaChaSealOpenRoundTrip() throws {
        let aead = ChaCha20Poly1305Aead()
        let key = Data(repeating: 0x01, count: 32)
        let nonce = Data(repeating: 0x02, count: 12)
        let pt = Data("hello lumos".utf8)
        let ad = Data("lumos.aad.v1".utf8)
        let ct = try aead.seal(key: key, nonce: nonce, plaintext: pt, ad: ad)
        let decoded = try aead.open(key: key, nonce: nonce, ciphertext: ct, ad: ad)
        XCTAssertEqual(decoded, pt)
    }

    func testAesGcmSealOpenRoundTrip() throws {
        let aead = Aes256GcmAead()
        let key = Data(repeating: 0x03, count: 32)
        let nonce = Data(repeating: 0x04, count: 12)
        let pt = Data(repeating: 0xFF, count: 64)
        let ad = Data("lumos.aad.v1".utf8)
        let ct = try aead.seal(key: key, nonce: nonce, plaintext: pt, ad: ad)
        XCTAssertEqual(try aead.open(key: key, nonce: nonce, ciphertext: ct, ad: ad), pt)
    }

    func testAeadTamperedTagFails() throws {
        let aead = ChaCha20Poly1305Aead()
        let key = Data(repeating: 0x05, count: 32)
        let nonce = Data(repeating: 0x06, count: 12)
        let pt = Data("tag fail test".utf8)
        let ad = Data("ad".utf8)
        var ct = try aead.seal(key: key, nonce: nonce, plaintext: pt, ad: ad)
        ct[ct.count - 1] ^= 0xFF
        XCTAssertThrowsError(try aead.open(key: key, nonce: nonce, ciphertext: ct, ad: ad)) { err in
            XCTAssertEqual(err as? CryptoFault, .authTagFail)
        }
    }

    func testRandomBytesLength() throws {
        let rng = OsSecureRandom()
        let bytes = try rng.bytes(32)
        XCTAssertEqual(bytes.count, 32)
    }
}
