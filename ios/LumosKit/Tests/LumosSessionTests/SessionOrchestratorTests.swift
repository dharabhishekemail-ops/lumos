import XCTest
@testable import LumosSession
@testable import LumosProtocol
@testable import LumosTransport

final class SessionOrchestratorTests: XCTestCase {

    func testDedupeWindowAcceptThenReject() {
        var w = DedupeWindow(capacity: 4)
        XCTAssertTrue(w.acceptIfFresh("a"))
        XCTAssertTrue(w.acceptIfFresh("b"))
        XCTAssertFalse(w.acceptIfFresh("a"), "duplicate must be rejected")
    }

    func testDedupeWindowEvictsOldestPastCapacity() {
        var w = DedupeWindow(capacity: 3)
        XCTAssertTrue(w.acceptIfFresh("a"))
        XCTAssertTrue(w.acceptIfFresh("b"))
        XCTAssertTrue(w.acceptIfFresh("c"))
        XCTAssertTrue(w.acceptIfFresh("d"))
        // "a" should have been evicted, so reusing it is now fresh again.
        XCTAssertTrue(w.acceptIfFresh("a"))
    }

    func testRetryPolicyBackoffCap() {
        let p = RetryPolicy(maxRetries: 5, initialBackoffMs: 200, backoffMaxMs: 1_000)
        XCTAssertEqual(p.backoff(forAttempt: 1), 200)
        XCTAssertEqual(p.backoff(forAttempt: 2), 400)
        XCTAssertEqual(p.backoff(forAttempt: 3), 800)
        XCTAssertEqual(p.backoff(forAttempt: 4), 1_000) // capped
        XCTAssertEqual(p.backoff(forAttempt: 10), 1_000)
    }

    func testReducerHelloThenAck() {
        let helloMsg = TypedMessage(
            envelope: Envelope(msgType: .hello, sessionId: "s_session01", messageId: "m_msg00001x", sentAtMs: 0, transport: .ble),
            payload: .hello(HelloPayload(protocolVersions: [1], transports: [.ble], aeadSuites: [.chacha20Poly1305],
                                         kdfSuites: [.hkdfSha256], curves: [.x25519], features: [], nonce: "AAAAAAAAAAAAAAAA"))
        )
        let ackMsg = TypedMessage(
            envelope: Envelope(msgType: .helloAck, sessionId: "s_session01", messageId: "m_msg00002x", sentAtMs: 1, transport: .ble),
            payload: .helloAck(HelloAckPayload(selectedProtocolVersion: 1, selectedTransport: .ble,
                                               selectedAead: .chacha20Poly1305, selectedKdf: .hkdfSha256,
                                               selectedCurve: .x25519, featuresAccepted: []))
        )
        var state: SessionState = .idle
        state = SessionReducer.reduce(state, msg: helloMsg)
        XCTAssertEqual(state, .helloSent)
        state = SessionReducer.reduce(state, msg: ackMsg)
        XCTAssertEqual(state, .helloAckReceived)
    }
}
