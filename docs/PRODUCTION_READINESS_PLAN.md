# Lumos Production Readiness Plan

**Document version:** 1.0
**Audience:** Engineering manager, product lead, founder/CEO
**Companion docs:** `docs/RTM.md`, `docs/ON_DEVICE_TEST_PLAN.md`, `docs/ROLLOUT_PLAN.md`, `docs/INCIDENT_RUNBOOK.md`

This document is the explicit gap analysis between **what this rebuild delivered** and **what production shipping requires**. It exists because the prior brief — "fully working production app fulfilling all requirements" — is a multi-month team effort, not a single-conversation deliverable. This document tells you exactly what's left, who needs to do it, what depends on what, and roughly how long each piece takes.

## What this rebuild delivered (verified)

| Layer                           | Status      | Evidence                                     |
|---------------------------------|-------------|----------------------------------------------|
| Single canonical wire format     | Done        | `schemas/protocol-envelope.schema.json`     |
| Canonical Python codec           | Done        | `tools/canonical_codec.py`                  |
| iOS Swift codec, byte-equivalent | Done        | `LumosKit/.../ProtocolCodec.swift`           |
| Android Kotlin codec, byte-equivalent | Done   | `android/.../ProtocolCodec.kt`               |
| Conformance runner (real)        | Done        | 26/26 fixtures pass                          |
| Cross-platform interop simulator | Done        | 13/13 byte-identical                         |
| Property tests (Hypothesis)      | Done        | ~1,200 fuzzed envelopes pass                 |
| Fault simulator + soak           | Done        | Bit-flip rejection, 10K-iteration soak       |
| iOS LumosKit test suites         | Authored    | Run via `xcodebuild test` on macOS           |
| Android JUnit suites             | Present     | Run via `./gradlew testDebugUnitTest`        |
| Signed-config schema + runtime   | Done        | iOS `LumosConfig`; sign/verify Python tested |
| Replay-window semantics          | Done        | Pinned in pytest + iOS `DedupeWindow`        |
| Capability negotiation           | Done        | Pinned in pytest + iOS `CapabilityNegotiator`|
| RTM with honest tiers            | Done        | `docs/RTM.md` v1.4                           |

**Test counts on this rebuild:**
- pytest: 121/121
- conformance: 26/26
- interop simulator: 13/13

## What production shipping requires (not yet done)

The list below is grouped by rough dependency order. "Effort" is in person-days for one engineer with the relevant specialty, ignoring meetings and review.

### Group A — platform builds verified (must do first)

These unblock everything else. None of them can be done in this Linux environment.

| Item                                                     | Effort | Owner          | Depends on |
|----------------------------------------------------------|-------:|----------------|------------|
| `xcodegen generate` produces clean `.xcodeproj`           |    0.5 | iOS engineer   | macOS, XcodeGen |
| `xcodebuild test -scheme LumosKit-Package` passes         |    1   | iOS engineer   | macOS, Xcode 15+ |
| `xcodebuild build -scheme LumosApp` produces signed IPA   |    2   | iOS engineer   | Apple Developer account, signing certs |
| `./gradlew testDebugUnitTest` passes                      |    1   | Android engineer | Android SDK 34+ |
| `./gradlew assembleRelease` produces signed APK / AAB     |    2   | Android engineer | Android keystore |
| CI on GitHub Actions wired to gate every PR               |    2   | DevOps         | GH org, runner credits |

**Total Group A:** ~8.5 person-days. Until Group A is green, nothing downstream can ship.

### Group B — radio transports (BLE / Wi-Fi / QR)

This is the biggest piece of remaining work. It cannot be unit-tested in any meaningful way; only on-device verification works.

| Item                                                              | Effort | Owner            | Notes |
|-------------------------------------------------------------------|-------:|------------------|-------|
| iOS BLE central + peripheral via CoreBluetooth                     |   12   | iOS engineer (BLE experience) | Pairing logic, MTU negotiation, service/characteristic UUIDs locked |
| iOS Local Network discovery via NWConnection / Bonjour            |    8   | iOS engineer     | Lossy, requires graceful fallback to BLE |
| iOS QR scanning via AVCaptureSession                              |    3   | iOS engineer     | Permission flow + low-light handling |
| Android BLE central + peripheral via BluetoothLeScanner / GattServer |  12 | Android engineer (BLE experience) | API quirks across OEMs |
| Android Wi-Fi Aware via WifiAwareManager                          |    8   | Android engineer | Limited device support; needs OEM matrix |
| Android QR scanning via CameraX + ML Kit                          |    3   | Android engineer | Permission flow |
| Cross-platform BLE interop (a real Android handshakes a real iPhone) |   5 | Both, together   | First-time integration is always longer than expected |
| Test cells A, B, C, D, E from `ON_DEVICE_TEST_PLAN.md`            |    5   | QA               | Requires 4 devices |

**Total Group B:** ~56 person-days. Highest risk: BLE behavior is wildly inconsistent across Android OEMs (Samsung, Pixel, Xiaomi, OnePlus all differ). Budget time for an OEM matrix.

### Group C — UI flows (per-screen)

The screens exist as SwiftUI / Compose stubs. Wiring them to LumosKit / Android core modules and making them feel right is straightforward but not free.

| Screen                       | iOS effort | Android effort | Combined notes |
|------------------------------|-----------:|---------------:|----------------|
| Onboarding (consent gates)    |       2    |       2        | Legal copy review must precede coding |
| Profile (alias, tags, intent) |       2    |       2        | Image handling for avatars is +2 each |
| Discovery (peer list + filters) |     3    |       3        | Visual polish + RSSI-to-signal-bars |
| Requests (incoming / outgoing) |      2    |       2        |                |
| Chat (1:1, with media)         |       4    |       4        | Media UX (preview, retry, percent) |
| Safety (block, report, log)    |       3    |       3        |                |
| Settings (every flag)          |       2    |       2        |                |
| Diagnostics (debug only)       |       1    |       1        | Hidden behind config flag |

**Total Group C:** ~36 person-days (18 each platform).

### Group D — monetization

| Item                                                     | Effort | Owner | Notes |
|----------------------------------------------------------|-------:|-------|-------|
| iOS StoreKit 2 integration with receipt verification     |    5   | iOS engineer | Subscription state, refund handling |
| Android Play Billing v6+ integration with receipt verification | 5 | Android engineer | Same complexity |
| Server-side receipt validation endpoint (or self-validation) | 4 | Backend engineer | Pure self-validation is risky for refunds |
| Sandbox testing on both platforms                        |    3   | QA    | Apple sandbox + Play test track |
| Refund / restore flow                                    |    3   | Both  | Easy to skimp here; don't |

**Total Group D:** ~20 person-days. Biggest pitfall: dealing with cancellation / grace-period semantics correctly across both stores.

### Group E — content moderation, safety, abuse

| Item                                                               | Effort | Owner          | Notes |
|--------------------------------------------------------------------|-------:|----------------|-------|
| Local block list + persistence                                      |    2   | Both platforms | Fast |
| Report flow (capture metadata, queue for review)                    |    3   | Both platforms | Privacy-respecting (local hashing only) |
| Image filtering (NSFW detection)                                    |    5   | ML / one engineer | On-device model; no server-side scanning |
| Age-gate flow                                                       |    2   | Both platforms | Jurisdiction-specific |
| Safety center copy (legal-reviewed)                                 |    3   | Legal + product | Slow because of legal cycles |

**Total Group E:** ~15 person-days, with legal in the critical path.

### Group F — accessibility, localization, polish

| Item                                                                   | Effort |
|------------------------------------------------------------------------|-------:|
| VoiceOver / TalkBack labels on every interactive element               |    4   |
| Dynamic Type / large-font reflow                                       |    3   |
| AA-contrast audit + fixes                                              |    3   |
| RTL layout pass                                                        |    2   |
| Localization (en + 3 launch languages)                                 |    8   |
| Visual polish, animations, micro-interactions                          |    8   |

**Total Group F:** ~28 person-days. Easy to under-estimate; this is the difference between "feels like a hobby project" and "feels like a product."

### Group G — release engineering, security, legal

| Item                                                            | Effort | Notes |
|-----------------------------------------------------------------|-------:|-------|
| Crash reporting integration (Sentry / Crashlytics)              |    2   | |
| Telemetry / counter integration                                  |    3   | Privacy-respecting, defined in `docs/PERF_BUDGET.md` |
| Signing key management (config signing key + app signing keys)  |    5   | Hardware-backed; rotation drill |
| Threat model + security review                                  |   10   | Outside firm OR senior security engineer for ~2 weeks |
| Privacy review + DPIA                                            |    5   | Legal + privacy |
| App Store / Play Store metadata, screenshots, descriptions      |    5   | Marketing + product |
| Privacy nutrition labels (iOS) / Data safety form (Play)        |    3   | Product + privacy |
| App review submission process (rejections, iterations)           |    5   | First submission usually gets rejected once |

**Total Group G:** ~38 person-days, with security review on the critical path.

### Group H — load, soak, performance

| Item                                                  | Effort | Notes |
|-------------------------------------------------------|-------:|-------|
| Test cell K (4-hour soak) on real devices             |    2   | QA, both platforms |
| Test cell L (crowded RF) on real venue                 |    2   | QA |
| Battery profile per OEM matrix                        |    5   | QA |
| Memory profile per platform                            |    3   | QA |
| Performance budgets enforced in CI                    |    3   | DevOps |

**Total Group H:** ~15 person-days.

---

## Total: ~217 person-days of remaining work

Spread across approximate roles:
- iOS engineer: ~50 days
- Android engineer: ~55 days
- Backend / DevOps: ~15 days
- QA: ~20 days
- ML engineer: ~5 days
- Privacy / legal: ~10 days
- Security: ~10 days
- Product / marketing: ~10 days
- Designer: ~10 days

Calendar time, with a small focused team (2 mobile engineers, 1 backend, 1 QA, 1 PM, occasional specialists), is **roughly 4-5 months** of execution after Group A is green. With a single full-stack engineer, it's effectively impossible — the BLE work alone consumes a specialist for two months.

## Critical-path summary

If you cut everything that's not strictly required to ship:

```
Group A (platform builds)         8.5 days  →  unblocks everything
  └─ Group B (transports)         56 days   →  unblocks usable app
       └─ Group C (UI flows)      36 days   →  unblocks store submission
            └─ Group D (billing)  20 days   →  unblocks monetization
            └─ Group G (security) 10 days   →  unblocks store approval
            └─ Group F (a11y)      4 days   →  unblocks AA compliance
            └─ Group H (soak)      2 days   →  unblocks confidence
```

**Critical path:** ~136 days assuming perfect parallelism. With a 2-engineer mobile team you're looking at 4-5 months calendar.

## Recommended sequencing

1. **Weeks 1–2:** Group A. Get the builds green on real toolchains. Pull `make all` results into a CI gate. Don't write any new product code until this lands.
2. **Weeks 3–10:** Group B in parallel with simplest possible UI for Group C (text-only chat, no media). The two engineers pair on the first cross-platform handshake; that single integration is the project's highest-risk milestone.
3. **Weeks 11–14:** Finish Group C; layer in Group D and Group F.
4. **Weeks 15–16:** Group E (safety) plus Group G (security review starts; expect 2 weeks).
5. **Weeks 17–18:** Group H + bug-bash + first store submission.
6. **Weeks 19+:** Phased rollout per `docs/ROLLOUT_PLAN.md`.

## Honest closing note

The protocol layer is in a verifiably good state. Every claim in `docs/TEST_REPORT.md` is reproducible by running `make all`. That is not the same as a shippable product. Don't ship Lumos until everything in this document is green; the consequence of shipping early on a peer-to-peer encrypted-chat app that handles user-uploaded content and money is bad on every axis (privacy harm, store rejection, refund disputes, security disclosure).

If the team or budget for the work above isn't available, the smaller deliverable that IS achievable in the next few weeks is a **technical preview / dogfood build**:
- iOS only (cut Android scope)
- BLE only (cut Wi-Fi Aware + QR)
- Text only (cut media)
- No monetization, no ads
- TestFlight only (no public store)
- Diagnostics panel always on

That preview tests roughly 60 % of this RTM and produces real signal on whether the core product idea works, at maybe 30 % of the cost. Many small launches go this route.
