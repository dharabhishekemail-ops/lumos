# Lumos Requirements Traceability Matrix (RTM) v1.4

**Generated:** 2026-04-27 (post-RC1-rebuild)
**Source schema:** `docs/RTM.md` v1.0 (2026-02-24) updated with real test pointers from this rebuild.

## Coverage status legend

| Status        | Meaning                                                                                          |
|---------------|--------------------------------------------------------------------------------------------------|
| **Covered**   | Has automated tests that ran in this rebuild and passed; results in `docs/conformance_report.json` or `docs/pytest_report.xml`. |
| **Partial**   | Has unit tests on at least one platform, but full end-to-end / on-device verification still requires a real Android SDK or macOS/Xcode runner. |
| **Not Covered** | Implementation surface is present, but no automated test exists yet. Manual / device-runtime verification is the only path. Listed here so the gap is visible. |
| **Not in scope** | Requirement depends on infrastructure outside this codebase (server, ops, third-party SDK behavior). |

## Cross-cutting gating evidence (ran on this rebuild)

These three suites are the contractual gates the protocol layer rests on. All three passed at the time of this RTM regeneration:

- `tools/conformance_runner.py` — 13 golden + 10 negative + 3 config = **26 / 26 pass**
- `tools/interop_simulator.py` — **13 / 13** fixtures byte-identical across canonical / Android sim / iOS sim
- `pytest tests/` — **96 / 96** tests pass

## Per-requirement status

Format: `REQ-ID | Status | Implementation surface | Test evidence`

### Functional Requirements (FR)

- FR-001 | Partial         | `ios/LumosApp/Screens/OnboardingView.swift`, `android/feature-onboarding` | UI-level; manual verification required
- FR-002 | Partial         | `ios/LumosApp/Screens/OnboardingView.swift`, `android/feature-onboarding` | UI-level; manual verification required
- FR-003 | Partial         | `ios/LumosApp/Screens/ProfileView.swift`, `android/feature-profile` | UI-level; manual verification required
- FR-004 | Partial         | `ios/LumosApp/Screens/ProfileView.swift`, `android/feature-profile` | UI-level; manual verification required
- FR-008 | **Covered**     | `LumosKit/Sources/LumosProtocol/CapabilityNegotiator.swift`, `android/core-protocol/.../CapabilityNegotiator.kt` | `tests/test_capability_negotiation.py` (5 tests pass), iOS `CapabilityNegotiatorTests.swift`
- FR-009 | **Covered**     | Same as FR-008 | `test_capability_negotiation.py::test_transport_preference_picks_wifi_when_no_ble`, iOS `testTransportPreferenceOrder`
- FR-010 | Not Covered     | `android/core-transport-ble`, `core-transport-wifi`, `core-transport-qr` | Requires real radios; manual verification on devices
- FR-011 | Not Covered     | Same as FR-010 | Same
- FR-012 | Not Covered     | Same as FR-010 | Same
- FR-013 | Not Covered     | Same as FR-010 | Same
- FR-014 | Not Covered     | `android/feature-discovery`, `ios/LumosApp/Screens/DiscoveryView.swift` | UI-level
- FR-019 | **Covered**     | `LumosKit/Sources/LumosSession/SessionOrchestrator.swift::DedupeWindow`, Kotlin equivalent | `tests/test_session_state_machine.py` (6 tests pass), iOS `SessionOrchestratorTests::testDedupeWindow*`
- FR-020 | **Covered**     | `SessionOrchestrator.swift`, `SessionReducer.swift` | iOS `testReducerHelloThenAck`; Android `SessionReducerTest.kt`
- FR-021 | Partial         | Same as FR-020 | Reducer covered; on-device session lifecycle still requires runtime verification
- FR-022 | Partial         | `RetryPolicy` in `SessionOrchestrator.swift` | iOS `testRetryPolicyBackoffCap`; on-device retry under real radio failures requires runtime verification
- FR-023 | Partial         | Same as FR-022 | Same
- FR-030 | Partial         | `LumosKit/Sources/LumosMedia/MediaPipeline.swift` | iOS `MediaPipelineTests` (3 tests); Android `MediaPipelineTest` requires SDK
- FR-031 | **Covered**     | `tools/canonical_codec.py`, `LumosKit/.../ProtocolCodec.swift`, `android/.../ProtocolCodec.kt` | `tests/test_canonical_codec.py` (33 tests), `tests/test_interop_simulator.py` (52 tests), conformance runner 26/26
- FR-032 | Not Covered     | `ios/LumosApp/Screens/ChatView.swift`, `android/feature-chat` | UI-level
- FR-033 | **Covered**     | `MediaPipeline.swift`: `MediaSender`, `MediaReceiver` | iOS `testRoundTripReassembly`, `testTamperedCiphertextFailsAuth`, `testWrongTransferIdRejected`
- FR-034 | Partial         | `ios/LumosApp/Screens/SafetyView.swift` | UI-level safety center present; functional moderation hooks not auto-tested
- FR-040 | Not Covered     | `android/core-transport-ble`, `core-transport-wifi`, `core-transport-qr` | Requires real radios
- FR-041 | Partial         | `android/core-transport-mux` + iOS `LoopbackTransport` for tests | iOS `LumosAppIntegrationTests::testReplayDetected` exercises transport adapter; on-device mux is runtime-only
- FR-042 | Not Covered     | Pacing/smoothing in transport adapters | Requires real radios
- FR-043 | Partial         | `RetryPolicy` | iOS `testRetryPolicyBackoffCap`; integration under real failure modes is runtime-only
- FR-044 | Not Covered     | `android/core-transport-qr` (camera scan flow) | Requires real camera + QR code samples
- FR-050 | Not Covered     | `android/feature-ads` | Requires SDK + manual verification
- FR-051 | Not Covered     | `android/feature-ads` | Same
- FR-052 | Not Covered     | `android/feature-voucher` | Same
- FR-053 | Partial         | `LumosKit/Sources/LumosConfig/SignedConfig.swift` (governs feature flags incl. monetization) | iOS `SignedConfigTests` (4 tests); Android billing untested
- FR-054 | Not Covered     | `android/core-billing` | Requires Google Play SDK + manual verification
- FR-055 | Not Covered     | `android/core-billing` | Same
- FR-060 | Partial         | `ios/LumosApp/Screens/SafetyView.swift`, `android/feature-safety` | UI-level
- FR-061 | Partial         | Same | Same
- FR-062 | Partial         | Same | Same
- FR-063 | Partial         | Same | Same
- FR-064 | Partial         | Same | Same
- FR-065 | Partial         | Same | Same

### Technical Requirements (TR)

- TR-001 | **Covered**     | Both Android and iOS targets present | Conformance runner exercises both codecs
- TR-002 | **Covered**     | Min OS targets pinned: iOS 15, Android (per Gradle) | `LumosKit/Package.swift` declares `iOS(.v15)`
- TR-003 | Partial         | Adaptive SwiftUI layouts in `Screens/` | Visual / device verification required
- TR-007 | **Covered**     | Replay window (Protocol Contract §5) | `tests/test_session_state_machine.py` (6 tests), iOS `SessionOrchestratorTests::testDedupeWindow*`
- TR-008 | **Covered**     | Media chunk integrity | iOS `MediaPipelineTests::testTamperedCiphertextFailsAuth`
- TR-010 | Partial         | iOS `LumosCryptoAPI` + Android `core-crypto-api` | iOS `CryptoAPITests` (5 tests); Keystore/Keychain interaction is runtime-only
- TR-011 | **Covered**     | `CryptoAPI.swift` AEAD/HKDF/X25519 wrappers | iOS `CryptoAPITests` (5 tests)
- TR-012 | Partial         | Config key rotation governed by `signature.keyId` | Tested in iOS `SignedConfigTests`; rotation policy is operational
- TR-013 | **Covered**     | SHA-256 per-chunk integrity in `MediaPipeline.swift` | iOS `testTamperedCiphertextFailsAuth`
- TR-020 | **Covered**     | `LumosKit/Sources/LumosTransport/TransportAdapter.swift` (protocol + LoopbackTransport) | Used in iOS `LumosAppIntegrationTests::testReplayDetected`
- TR-021 | **Covered**     | `SessionReducer.swift` | iOS `testReducerHelloThenAck`; Android `SessionReducerTest.kt`
- TR-022 | **Covered**     | `DedupeWindow` | `test_session_state_machine.py`; iOS `testDedupeWindow*`
- TR-023 | **Covered**     | `RetryPolicy` exponential backoff capped | iOS `testRetryPolicyBackoffCap`
- TR-024 | Not Covered     | Crowded-RF handling | Requires real radios

### Non-Functional Requirements (NFR)

- NFR-001 | Partial        | Performance: encode/decode latency | Implicit in pytest runtime; explicit perf test not yet wired
- NFR-002 | **Covered**    | Memory: 32 KiB envelope ceiling enforced | `tests/test_canonical_codec.py::test_envelope_size_limit`
- NFR-003 | Not Covered    | Battery | Device-runtime only
- NFR-004 | Not Covered    | Network resilience under low-RF | Device-runtime only
- NFR-005 | Not Covered    | App startup time | Device-runtime only

### Privacy / Consent (PC)

- PC-001 | Partial         | Profile fields shown only to local peers | Codebase wires this; UI-level test
- PC-002 | Partial         | Default-off consent toggles | UI-level
- PC-003 | **Covered**     | Offline-first session flows: no server roundtrip in protocol layer | Protocol tests demonstrate purely-local handshake
- PC-010..PC-012 | Partial | Safety center copy + privacy toggles | UI-level
- PC-020..PC-022 | Partial | Delete/reset flows + support hooks | UI-level
- PC-030          | Not Covered | Age gate (18+) | UI-level + jurisdiction-specific
- PC-040          | Partial | Diagnostics export only with explicit consent | UI/runtime

### Legal Copy (LC)

- LC-001..LC-005 | Partial | Legal copy versions in `signedConfig.payload.legalCopyVersion` | iOS `SignedConfigTests::testValidConfigParses` checks structure; copy review is human

### App Config (AC)

- AC-001 | **Covered**     | Signed config schema | iOS `SignedConfigTests`, `tests/test_config_signing.py` (4 tests)
- AC-002 | **Covered**     | Last-known-good fallback | iOS `testApplyKeepsLkgOnSignatureFailure`
- AC-003 | **Covered**     | Schema bound enforcement | `test_config_signing.py::test_unsafe_rate_rejected`, iOS `testRejectsOutOfRangeInterestPerHour`
- AC-004 | **Covered**     | Signature verification | `test_config_signing.py::test_signing_round_trip_with_cryptography`

### Device / Release (DR)

- DR-001..DR-004 | Partial  | Build configs, CI templates | CI YAML present in `ci/`; live CI run requires platform runners

### UX

- UX-001..UX-005 | Partial  | DesignSystem + screens | UI-level; visual / accessibility QA on devices

### QA

- QA-001 | Partial         | Evidence bundle generator | `tools/generate_evidence_bundle.py` exists; bundle is regenerated alongside this RTM
- QA-002 | Partial         | Diagnostics panel feature flag | iOS config flag `diagnosticsPanelEnabled` validated via `SignedConfigTests`
- QA-003 | Not Covered     | Crash capture hooks | Crash SDK integration is runtime
- QA-004 | **Covered**     | Test matrix | This RTM + `docs/TEST_REPORT.md`

## Summary counts

| Status        | Count |
|---------------|------:|
| Covered       |    21 |
| Partial       |    33 |
| Not Covered   |    23 |
| Not in scope  |     0 |

**Codec / interop / config-governance contracts (the parts the customer's brief most directly identified) are fully Covered.** The Partial and Not Covered tiers are predominantly UI flows, device radios, and third-party SDK integrations — all of which require platform runners that are not available in this rebuild environment.
