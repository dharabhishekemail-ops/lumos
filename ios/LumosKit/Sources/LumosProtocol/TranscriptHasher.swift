// TranscriptHasher — produces the SHA-256 hex digest of a canonical Hello+HelloAck
// transcript, used as KDF associated data per Lumos Crypto Protocol Spec v1.0 §3.
//
// Rule: the digest is computed over the *canonical wire bytes* of (Hello envelope+payload)
// concatenated with (HelloAck envelope+payload), separated by a single 0x1F (US) byte.
// Both peers MUST compute the same hex string from the same exchange.

import Foundation
import CryptoKit

public enum TranscriptHasher {

    public static func transcriptHash(helloMsg: TypedMessage, ackMsg: TypedMessage) throws -> String {
        let helloBytes = try ProtocolCodec.encode(helloMsg)
        let ackBytes = try ProtocolCodec.encode(ackMsg)
        var combined = Data()
        combined.append(helloBytes)
        combined.append(0x1F)
        combined.append(ackBytes)
        let digest = SHA256.hash(data: combined)
        return Data(digest).hexLowercase()
    }
}
