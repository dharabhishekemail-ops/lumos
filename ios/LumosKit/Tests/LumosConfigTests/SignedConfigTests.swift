import XCTest
@testable import LumosConfig
@testable import LumosCryptoAPI

final class SignedConfigTests: XCTestCase {

    func testValidConfigParses() throws {
        let json = #"""
        {
          "schemaVersion": 1,
          "configVersion": "1.0.0",
          "issuedAt": "2026-03-01T00:00:00Z",
          "expiresAt": "2030-09-01T00:00:00Z",
          "payload": {
            "featureFlags": {"adsTabEnabled": true, "vouchersEnabled": false, "operatorModeEnabled": false, "diagnosticsPanelEnabled": true},
            "transport": {"priorityOrder": ["WIFI","BLE","QR"], "retryBudget": 5, "backoffMaxMs": 8000},
            "rateLimits": {"interestPerHour": 30, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0",
            "monetization": {"freePreviewMinutes": 30, "skus": []},
            "rolloutPercent": 100
          },
          "signature": {"alg":"ed25519","keyId":"k1","sigB64":"AAAA"}
        }
        """#
        let pack = try JSONDecoder().decode(SignedConfigPack.self, from: Data(json.utf8))
        XCTAssertNoThrow(try ConfigValidator.validateSchema(pack))
    }

    func testRejectsOutOfRangeInterestPerHour() throws {
        let json = #"""
        {
          "schemaVersion": 1,
          "configVersion": "1.0.0",
          "issuedAt": "2026-03-01T00:00:00Z",
          "expiresAt": "2030-09-01T00:00:00Z",
          "payload": {
            "featureFlags": {},
            "transport": {"priorityOrder":["WIFI"], "retryBudget":3, "backoffMaxMs":4000},
            "rateLimits": {"interestPerHour": 999, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0"
          },
          "signature": {"alg":"ed25519","keyId":"k1","sigB64":"AAAA"}
        }
        """#
        let pack = try JSONDecoder().decode(SignedConfigPack.self, from: Data(json.utf8))
        XCTAssertThrowsError(try ConfigValidator.validateSchema(pack)) { err in
            XCTAssertEqual(err as? ConfigError, .unsafeBound(field: "interestPerHour"))
        }
    }

    func testRejectsBadSchemaVersion() throws {
        let json = #"""
        {
          "schemaVersion": 99,
          "configVersion": "1.0.0",
          "issuedAt": "2026-03-01T00:00:00Z",
          "expiresAt": "2030-09-01T00:00:00Z",
          "payload": {
            "featureFlags": {},
            "transport": {"priorityOrder":["WIFI"], "retryBudget":3, "backoffMaxMs":4000},
            "rateLimits": {"interestPerHour": 30, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0"
          },
          "signature": {"alg":"ed25519","keyId":"k1","sigB64":"AAAA"}
        }
        """#
        let pack = try JSONDecoder().decode(SignedConfigPack.self, from: Data(json.utf8))
        XCTAssertThrowsError(try ConfigValidator.validateSchema(pack))
    }

    func testApplyKeepsLkgOnSignatureFailure() throws {
        let json = #"""
        {
          "schemaVersion": 1,
          "configVersion": "1.0.0",
          "issuedAt": "2026-03-01T00:00:00Z",
          "expiresAt": "2030-09-01T00:00:00Z",
          "payload": {
            "featureFlags": {},
            "transport": {"priorityOrder":["WIFI"], "retryBudget":3, "backoffMaxMs":4000},
            "rateLimits": {"interestPerHour": 30, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0"
          },
          "signature": {"alg":"ed25519","keyId":"unknown","sigB64":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}
        }
        """#
        let runtime = ConfigRuntime(verifier: try CryptoKitEd25519Verifier(trustAnchors: [:]))
        XCTAssertNil(runtime.current)
        XCTAssertThrowsError(try runtime.apply(Data(json.utf8)))
        XCTAssertNil(runtime.current, "signature failure must NOT mutate current LKG")
    }
}
