# Lumos RC2.1 Acceptance Checklist

Document version: 1.0  
Baseline source: `Lumos_RC2_FIXED.zip` adopted as the new source of truth.  
Purpose: Short go/no-go checklist that maps the RC2 rebuild to the on-device test plan and phased rollout gates.

## A. Local protocol / config / evidence gates

These gates must pass on every release-candidate commit before platform/device testing starts.

| Gate | Command | Required result | Evidence artifact |
|---|---|---|---|
| Python dependencies available | `make python-deps` | Exit 0; no missing packages | terminal log + `docs/python_dependency_lock.json` |
| Dependency evidence generated | `make python-lock` | Exit 0 | `docs/python_dependency_lock.json`, `docs/python_dependency_lock.txt` |
| Python protocol/config/session tests | `make python-test` | All tests pass | `docs/pytest_report.xml` |
| Protocol conformance | `make conformance` | 26/26 pass | `docs/conformance_report.json` |
| Android/iOS codec simulator interop | `make interop-sim` | 13/13 pass | `docs/interop_simulator_report.json` |
| Signed config schema validation | `make validate-config` | Valid config accepted; negative configs rejected by conformance suite | CLI output + conformance report |
| Evidence pack generation | `make evidence` | `evidence_bundle.zip` created | `evidence_bundle.zip`, `docs/evidence_summary.md` |

Recommended one-command local gate:

```bash
python3 -m pip install -r tools/requirements.txt
make all
```

## B. Platform build gates

These cannot be completed on a Linux-only environment. They must be executed by platform engineers before RC2 can be called device-ready.

| Platform | Command / action | Required result | Maps to |
|---|---|---|---|
| iOS project generation | `cd ios/LumosApp && xcodegen generate` | Clean `.xcodeproj` generated | Production Readiness Group A |
| iOS package/app tests | `make ios-test` | XCTest pass; result bundle generated | RTM Partial rows; iOS LumosKit tests |
| Android unit tests | `make android-test` | Gradle unit tests pass | RTM Partial rows; Android JUnit |
| Android release build | `cd android && ./gradlew assembleRelease` | Signed APK/AAB produced | Release readiness |
| iOS signed build | Xcode archive / Fastlane | Signed IPA/TestFlight build produced | Release readiness |

## C. On-device test gates

Execute `docs/ON_DEVICE_TEST_PLAN.md` using the RC2 build and the same signed config on both platforms.

| Test cell | Scope | Required result before rollout |
|---|---|---|
| A | BLE peer discovery | Peers appear/disappear/stale correctly; no profile leakage |
| B | Handshake + capability negotiation | BLE fallback, no-common-transport, incompatible-version paths behave correctly |
| C | Chat + media | Text and image transfer decrypt, ACK, resume, reject tamper |
| D | Wi-Fi transport/migration | BLE→Wi-Fi migration succeeds without message duplication/loss |
| E | QR bootstrap | QR scan succeeds under normal cases and replay is rejected |
| F | Replay/dedupe | Duplicate/replay messages are dropped per window semantics |
| G | Signed config rollout | Invalid signature and unsafe bounds retain LKG; valid config applies within 60s |
| H | Billing | StoreKit / Play Billing purchases, cancellation, restore, tamper handling verified |
| I | Privacy/safety/consent | Ads opt-out, delete data, block/report, age gate verified |
| J+ | Accessibility/soak/crowded RF/battery | No release-blocking failures |

## D. Rollout gates

Use `docs/ROLLOUT_PLAN.md` as the release playbook.

| Phase | Entry requirement | Exit requirement |
|---|---|---|
| Phase 0 pre-flight | `make all` green; on-device plan signed off; security/privacy/crash/telemetry/kill-switch verified | No red pre-flight item |
| Phase 1 internal alpha | TestFlight + Play Internal; diagnostics enabled; monetization off | crash-free >= 99.5% for 72h, zero P0 |
| Phase 2 closed beta | 500 invited testers/platform; monetization off | 7 consecutive green days; >=50 Android↔iOS chats |
| Phase 3 open beta | public listing, no marketing push; vouchers enabled | 24h soak per region |
| Phase 4 public monetization | ads/vouchers enabled by signed config | monitor kill-switch triggers continuously |

## E. Incident readiness gate

Before public release, verify that the on-call team can execute `docs/INCIDENT_RUNBOOK.md` for:

- P0 data exposure
- P0 crash spike
- P0 codec drift
- P1 config rejection spike
- P1 billing failure
- P2 discovery slow
- P2 battery regression

## F. Go / no-go rule

RC2.1 is acceptable for the next stage only when:

1. `make all` passes on the release commit.
2. iOS and Android platform unit tests pass on real toolchains.
3. All mandatory on-device test cells have signed evidence.
4. Rollout kill-switch drill passes with signed config and LKG fallback.
5. Security/privacy reviews have no open P0/P1 findings.

If any item above is red, do not proceed to external users.
