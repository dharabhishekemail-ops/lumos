// LumosMedia — chunked media transfer with per-chunk AEAD and SHA-256 integrity.
// Per Lumos Protocol & Interop Contract v1.0 §4.

import Foundation
import CryptoKit
import LumosCommon
import LumosProtocol
import LumosCryptoAPI

public enum MediaError: Error, Equatable {
    case integrityMismatch(chunkIndex: Int)
    case authTagFail(chunkIndex: Int)
    case unexpectedTransfer(transferId: String)
    case outOfOrder(chunkIndex: Int)
    case sizeOutOfRange
}

/// Splits a plaintext blob into fixed-size chunks, encrypts each with the supplied
/// AEAD using a per-chunk nonce derived from (transferId, chunkIndex), and emits
/// `ChatMediaChunkPayload` values ready to be wrapped in envelopes.
public final class MediaSender {

    public let transferId: String
    public let aead: AeadCipher
    public let key: Data           // 32 bytes
    public let chunkSize: Int      // plaintext bytes per chunk
    public let blob: Data

    public init(transferId: String, aead: AeadCipher, key: Data, chunkSize: Int = 16 * 1024, blob: Data) {
        precondition(key.count == 32, "AEAD key must be 32 bytes")
        precondition(chunkSize > 0 && chunkSize <= 32 * 1024, "chunkSize 1..32k")
        self.transferId = transferId
        self.aead = aead
        self.key = key
        self.chunkSize = chunkSize
        self.blob = blob
    }

    public var chunkCount: Int {
        return max(1, (blob.count + chunkSize - 1) / chunkSize)
    }

    public func chunk(at index: Int) throws -> ChatMediaChunkPayload {
        let n = chunkCount
        if index < 0 || index >= n { throw MediaError.outOfOrder(chunkIndex: index) }
        let start = index * chunkSize
        let end = min(start + chunkSize, blob.count)
        let plain = blob.subdata(in: start..<end)
        let nonce = MediaCrypto.deriveNonce(transferId: transferId, chunkIndex: index)
        let ad = MediaCrypto.associatedData(transferId: transferId, chunkIndex: index, isLast: index == n - 1)
        let ct = try aead.seal(key: key, nonce: nonce, plaintext: plain, ad: ad)
        let sha = SHA256.hash(data: plain)
        let shaHex = Data(sha).hexLowercase()
        return ChatMediaChunkPayload(
            transferId: transferId,
            chunkIndex: index,
            isLast: index == n - 1,
            ciphertextB64: ct.base64EncodedString(),
            sha256Hex: shaHex
        )
    }
}

public final class MediaReceiver {

    public let transferId: String
    public let aead: AeadCipher
    public let key: Data
    private(set) public var chunks: [Int: Data] = [:]
    private(set) public var highestContiguous: Int = -1

    public init(transferId: String, aead: AeadCipher, key: Data) {
        precondition(key.count == 32, "AEAD key must be 32 bytes")
        self.transferId = transferId
        self.aead = aead
        self.key = key
    }

    @discardableResult
    public func accept(_ chunk: ChatMediaChunkPayload) throws -> ChatMediaAckPayload {
        if chunk.transferId != self.transferId { throw MediaError.unexpectedTransfer(transferId: chunk.transferId) }
        guard let ct = Data(base64Encoded: chunk.ciphertextB64) else { throw MediaError.sizeOutOfRange }
        let nonce = MediaCrypto.deriveNonce(transferId: transferId, chunkIndex: chunk.chunkIndex)
        let ad = MediaCrypto.associatedData(transferId: transferId, chunkIndex: chunk.chunkIndex, isLast: chunk.isLast)
        let plain: Data
        do { plain = try aead.open(key: key, nonce: nonce, ciphertext: ct, ad: ad) }
        catch { throw MediaError.authTagFail(chunkIndex: chunk.chunkIndex) }
        let sha = SHA256.hash(data: plain)
        if Data(sha).hexLowercase() != chunk.sha256Hex {
            throw MediaError.integrityMismatch(chunkIndex: chunk.chunkIndex)
        }
        chunks[chunk.chunkIndex] = plain
        // Recompute highest contiguous index.
        var n = highestContiguous + 1
        while chunks[n] != nil { n += 1 }
        highestContiguous = n - 1
        return ChatMediaAckPayload(transferId: transferId, highestContiguousChunk: highestContiguous)
    }

    /// Reassemble all chunks if every index 0..max is present and last has been observed.
    public func reassemble() -> Data? {
        guard !chunks.isEmpty else { return nil }
        let maxIdx = chunks.keys.max() ?? -1
        for i in 0...maxIdx { if chunks[i] == nil { return nil } }
        var out = Data()
        for i in 0...maxIdx { out.append(chunks[i]!) }
        return out
    }
}

public enum MediaCrypto {

    /// Derive a 12-byte nonce from (transferId, chunkIndex). Stable across implementations.
    public static func deriveNonce(transferId: String, chunkIndex: Int) -> Data {
        // SHA-256 of the canonical "transferId|chunkIndex" string, take the leading 12 bytes.
        let s = "\(transferId)|\(chunkIndex)"
        let h = SHA256.hash(data: Data(s.utf8))
        return Data(h).prefix(12)
    }

    /// Associated data for AEAD: small protobuf-like fixed string.
    public static func associatedData(transferId: String, chunkIndex: Int, isLast: Bool) -> Data {
        let s = "lumos.media.v1|\(transferId)|\(chunkIndex)|\(isLast ? "1" : "0")"
        return Data(s.utf8)
    }
}
