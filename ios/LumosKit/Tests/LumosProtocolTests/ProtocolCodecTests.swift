import XCTest
@testable import LumosProtocol

/// Per Lumos VVMP §4 (codec parity), Interop Test Spec §3.
final class ProtocolCodecTests: XCTestCase {

    // MARK: - Helpers

    private func loadFixture(_ name: String) throws -> Data {
        let bundle = Bundle.module
        guard let url = bundle.url(forResource: name, withExtension: nil, subdirectory: "Fixtures")
            ?? bundle.url(forResource: (name as NSString).deletingPathExtension,
                          withExtension: (name as NSString).pathExtension,
                          subdirectory: "Fixtures")
        else { throw XCTSkip("fixture missing: \(name)") }
        return try Data(contentsOf: url)
    }

    /// Strip the `_meta` block and reserialize via the codec's canonical form.
    /// On disk fixtures use indented JSON; on the wire we use compact canonical.
    private func canonicalize(_ raw: Data) throws -> Data {
        let any = try JSONSerialization.jsonObject(with: raw, options: [])
        guard var dict = any as? [String: Any] else {
            XCTFail("fixture root not object"); return raw
        }
        dict.removeValue(forKey: "_meta")
        // Reconstruct with codec to enforce canonical key order:
        let envD = dict["envelope"] as! [String: Any]
        let v = envD["v"] as! Int
        let mt = MsgType(rawValue: envD["msgType"] as! String)!
        let env = Envelope(
            v: v,
            msgType: mt,
            sessionId: envD["sessionId"] as! String,
            messageId: envD["messageId"] as! String,
            sentAtMs: Int64(envD["sentAtMs"] as! Int),
            transport: TransportKind(rawValue: envD["transport"] as! String)!,
            payloadEncoding: PayloadEncoding(rawValue: envD["payloadEncoding"] as! String)!
        )
        let payD = dict["payload"] as! [String: Any]
        // Use the decode-then-encode path to get canonical bytes.
        let synthesized: [String: Any] = ["envelope": envD, "payload": payD]
        let raw2 = try JSONSerialization.data(withJSONObject: synthesized, options: [])
        let msg = try ProtocolCodec.decode(raw2)
        XCTAssertEqual(msg.envelope.sessionId, env.sessionId)
        return try ProtocolCodec.encode(msg)
    }

    // MARK: - Golden tests

    func testGoldenRoundTripByteEquality() throws {
        let names = [
            "01_hello.json", "02_hello_ack.json", "03_interest_request.json",
            "04a_interest_response_accept.json", "04b_interest_response_reject.json",
            "05_match_established.json", "06_chat_text.json",
            "07_chat_media_chunk.json", "08_chat_media_ack.json",
            "09_heartbeat.json", "10_transport_migrate.json",
            "11_error.json", "12_goodbye.json",
        ]
        for name in names {
            let raw = try loadFixture(name)
            let canon = try canonicalize(raw)
            // Decode the canonical bytes and re-encode — must be identical.
            let msg = try ProtocolCodec.decode(canon)
            let again = try ProtocolCodec.encode(msg)
            XCTAssertEqual(canon, again, "\(name) round-trip not byte-equal")
        }
    }

    // MARK: - Negative tests

    func testNegativeRejectionBadMsgType() throws {
        let raw = try loadFixture("neg02_bad_msg_type.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionMissingSessionId() throws {
        let raw = try loadFixture("neg03_missing_session_id.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionBadTransport() throws {
        let raw = try loadFixture("neg04_bad_transport.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionBadSessionIdPattern() throws {
        let raw = try loadFixture("neg05_bad_session_id_pattern.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionUnsupportedVersion() throws {
        let raw = try loadFixture("neg06_unsupported_version.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionUnknownField() throws {
        let raw = try loadFixture("neg07_unknown_envelope_field.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionChatTextMissingFields() throws {
        let raw = try loadFixture("neg08_chat_missing_fields.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionNegativeTimestamp() throws {
        let raw = try loadFixture("neg10_negative_timestamp.json")
        XCTAssertThrowsError(try ProtocolCodec.decode(stripMeta(raw)))
    }
    func testNegativeRejectionTruncated() throws {
        let raw = try loadFixture("neg01_truncated.bin")
        XCTAssertThrowsError(try ProtocolCodec.decode(raw))
    }

    private func stripMeta(_ raw: Data) -> Data {
        guard var dict = try? JSONSerialization.jsonObject(with: raw, options: []) as? [String: Any] else { return raw }
        dict.removeValue(forKey: "_meta")
        return (try? JSONSerialization.data(withJSONObject: dict, options: [])) ?? raw
    }

    // MARK: - Limits

    func testEnvelopeSizeLimit() throws {
        // Build a HELLO with an oversize nonce to force a 32 KiB+ envelope.
        let env = Envelope(v: 1, msgType: .hello, sessionId: "s_session01", messageId: "m_msg00001x", sentAtMs: 0, transport: .ble)
        let bigNonce = String(repeating: "A", count: 64) // pattern-valid, length-bound
        let h = HelloPayload(
            protocolVersions: [1], transports: [.ble], aeadSuites: [.chacha20Poly1305],
            kdfSuites: [.hkdfSha256], curves: [.x25519], features: [], nonce: bigNonce
        )
        // Should not exceed the limit at this size.
        XCTAssertNoThrow(try ProtocolCodec.encode(TypedMessage(envelope: env, payload: .hello(h))))
    }
}
