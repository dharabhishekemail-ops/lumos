import XCTest
@testable import LumosProtocol

final class CapabilityNegotiatorTests: XCTestCase {

    private let local = LocalCapabilities.defaultLocal

    private func remote(
        versions: [Int] = [1],
        transports: [TransportKind] = [.ble, .wifi, .qr],
        aead: [AeadSuite] = [.chacha20Poly1305, .aes256Gcm],
        kdf: [KdfSuite] = [.hkdfSha256],
        curves: [CurveSuite] = [.x25519],
        features: [FeatureFlag] = [.chatText, .chatMediaPhoto, .voucherQr]
    ) -> HelloPayload {
        HelloPayload(protocolVersions: versions, transports: transports,
                     aeadSuites: aead, kdfSuites: kdf, curves: curves,
                     features: features, nonce: "Tm9uY2VBQUFBQUFBQQ==")
    }

    func testHappyPath() {
        if case .ok(let ack) = CapabilityNegotiator.negotiate(local: local, remoteHello: remote()) {
            XCTAssertEqual(ack.selectedProtocolVersion, 1)
            XCTAssertEqual(ack.selectedTransport, .ble) // local prefers .ble
            XCTAssertEqual(ack.selectedAead, .chacha20Poly1305)
            XCTAssertEqual(ack.selectedCurve, .x25519)
            XCTAssertEqual(ack.featuresAccepted, [.chatText, .chatMediaPhoto, .voucherQr])
        } else {
            XCTFail("expected ok")
        }
    }

    func testIncompatibleVersion() {
        if case .incompatibleVersion = CapabilityNegotiator.negotiate(local: local, remoteHello: remote(versions: [2, 3])) {
        } else { XCTFail("expected incompatibleVersion") }
    }

    func testNoCommonTransport() {
        if case .noCommonTransport = CapabilityNegotiator.negotiate(local: local, remoteHello: remote(transports: [])) {
        } else { XCTFail("expected noCommonTransport") }
    }

    func testNoCommonAead() {
        // Force a remote with an AEAD list that has none of ours; we need a non-empty
        // value so build a single-element synthetic. Since AeadSuite only has 2 cases
        // both of which we support, we test by removing both from local — instead
        // just verify that an empty remote AEAD list returns noCommonAead.
        let r = remote(aead: [])
        if case .noCommonAead = CapabilityNegotiator.negotiate(local: local, remoteHello: r) {
        } else { XCTFail("expected noCommonAead") }
    }

    func testTransportPreferenceOrder() {
        // Local prefers BLE > WIFI > QR. Remote only offers WIFI+QR, so WIFI should win.
        let r = remote(transports: [.qr, .wifi])
        if case .ok(let ack) = CapabilityNegotiator.negotiate(local: local, remoteHello: r) {
            XCTAssertEqual(ack.selectedTransport, .wifi)
        } else { XCTFail("expected ok") }
    }
}
