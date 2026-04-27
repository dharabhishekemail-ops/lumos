import XCTest
@testable import LumosMedia
@testable import LumosCryptoAPI
@testable import LumosProtocol

final class MediaPipelineTests: XCTestCase {

    func testRoundTripReassembly() throws {
        let aead = ChaCha20Poly1305Aead()
        let key = Data(repeating: 0xA5, count: 32)
        let blob = Data(repeating: 0x42, count: 50_000)
        let sender = MediaSender(transferId: "xfer_aaaa1234", aead: aead, key: key, chunkSize: 16 * 1024, blob: blob)
        let receiver = MediaReceiver(transferId: "xfer_aaaa1234", aead: aead, key: key)
        for i in 0..<sender.chunkCount {
            let chunk = try sender.chunk(at: i)
            _ = try receiver.accept(chunk)
        }
        let recon = receiver.reassemble()
        XCTAssertEqual(recon, blob)
    }

    func testTamperedCiphertextFailsAuth() throws {
        let aead = ChaCha20Poly1305Aead()
        let key = Data(repeating: 0xA5, count: 32)
        let blob = Data(repeating: 0x42, count: 32)
        let sender = MediaSender(transferId: "xfer_aaaa1234", aead: aead, key: key, chunkSize: 32, blob: blob)
        let receiver = MediaReceiver(transferId: "xfer_aaaa1234", aead: aead, key: key)
        var chunk = try sender.chunk(at: 0)
        // Flip a bit in the ciphertext.
        var ct = Data(base64Encoded: chunk.ciphertextB64)!
        ct[0] ^= 0xFF
        chunk = ChatMediaChunkPayload(transferId: chunk.transferId, chunkIndex: chunk.chunkIndex,
                                      isLast: chunk.isLast, ciphertextB64: ct.base64EncodedString(),
                                      sha256Hex: chunk.sha256Hex)
        XCTAssertThrowsError(try receiver.accept(chunk))
    }

    func testWrongTransferIdRejected() throws {
        let aead = ChaCha20Poly1305Aead()
        let key = Data(repeating: 0xA5, count: 32)
        let blob = Data(repeating: 0x42, count: 32)
        let sender = MediaSender(transferId: "xfer_correct", aead: aead, key: key, chunkSize: 32, blob: blob)
        let receiver = MediaReceiver(transferId: "xfer_other00", aead: aead, key: key)
        let chunk = try sender.chunk(at: 0)
        XCTAssertThrowsError(try receiver.accept(chunk))
    }
}
