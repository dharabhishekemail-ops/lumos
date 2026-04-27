# Lumos On-Device Test Plan

**Document version:** 1.0
**Audience:** QA + on-call engineers, executed before each release candidate
**Required equipment:** 2× Android phones (API 31+), 2× iPhones (iOS 15+), one Android tablet (for split-screen testing), 4× USB cables, a Faraday-bag-or-equivalent low-RF environment for one of the test cells, a venue with crowded Wi-Fi (coffee shop / coworking floor) for soak testing.

This is the test plan that closes the RTM rows currently marked **Not Covered** because they require radios. Run it once on every release candidate; capture results in a fresh copy of `docs/Evidence_Bundle.md`.

## Pre-flight (10 min)

1. Devices flashed with the RC build. Both platforms use the same `signedConfig` (commit hash recorded).
2. All four devices factory-reset; airplane mode toggled on/off to clear cached transports.
3. Bluetooth and Wi-Fi enabled, location permission granted, camera permission granted (for QR).
4. Telemetry firehose tailing logs from each device (`adb logcat`, Console.app via Mac).

## Test cell A — BLE peer discovery (FR-010, FR-011)

| Step | Action                                               | Expected                                              | Actual |
|------|------------------------------------------------------|-------------------------------------------------------|--------|
| A.1  | Device A: open app, complete onboarding              | "Discovery" screen shows zero peers                   |        |
| A.2  | Device B: open app, set "visible in venue"           | Within 30s, A's screen shows B's preview profile      |        |
| A.3  | Move B 10 m away                                     | A's screen shows B with weakened RSSI                 |        |
| A.4  | Move B 30 m away or behind a wall                    | Within 60s, A's screen marks B as "lost"              |        |
| A.5  | Bring B back into range                              | Within 30s, B reappears on A's screen                 |        |
| A.6  | Verify the wire format on the BLE link              | A packet capture shows envelope JSON matching schema  |        |

**Failure modes to capture:** repeat-discovery loops (RSSI flapping faster than the dedupe window), discovery latency >60s, RSSI reported as 0 / sentinel value, profile fields leaked beyond what the user opted into.

## Test cell B — handshake + capability negotiation (FR-008, FR-009)

| Step | Action                                                                         | Expected                                                                                  | Actual |
|------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|--------|
| B.1  | A sends INTEREST_REQUEST to B over BLE                                         | B receives, A's session enters `helloAckReceived`                                          |        |
| B.2  | Disable Wi-Fi on both, retry handshake                                         | Negotiation falls back to BLE; selectedTransport = BLE                                    |        |
| B.3  | Disable BLE on B, leave Wi-Fi on                                               | A's negotiation result = `noCommonTransport`; A surfaces a friendly error                 |        |
| B.4  | Force-mismatch protocol versions (test build with `protocolVersions=[2]`)     | Negotiation result = `incompatibleVersion`; ERROR envelope sent with code INCOMPATIBLE_VERSION |       |
| B.5  | Capture the on-wire bytes of A's HELLO and B's HELLO_ACK                       | Bytes byte-equal what `tools/canonical_codec.py encode_dict` produces from the same envelope |    |

## Test cell C — chat + media (FR-030, FR-032, FR-033, TR-008, TR-013)

| Step | Action                                                                  | Expected                                                                                    | Actual |
|------|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|--------|
| C.1  | A → B: 1-line text chat                                                 | Decrypts at B; AEAD tag verifies; appears within 2s                                         |        |
| C.2  | A → B: 1 MiB image (≈64 chunks at 16 KiB)                               | All chunks ACK'd; image renders bit-identical at B; SHA-256 of each chunk verifies          |        |
| C.3  | While C.2 is in flight, force-quit A                                    | B's receiver holds chunks, sends NACK timeout; on A relaunch, transfer resumes from highest contiguous chunk |  |
| C.4  | While C.2 is in flight, A walks out of BLE range, comes back            | Transfer pauses; resumes after reconnect; final image still bit-identical                   |        |
| C.5  | Tamper test: modify one byte of one chunk's ciphertext in flight (test build with packet hook) | Receiver rejects with `authTagFail` event; log shows REPLAY_DETECTED or AUTH_TAG_FAIL |   |

## Test cell D — Wi-Fi Aware / WiFi-Direct (FR-012, FR-040)

| Step | Action                                                                   | Expected                                                                                | Actual |
|------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|--------|
| D.1  | Both devices in same BSSID, run handshake over Wi-Fi only                | selectedTransport = WIFI; throughput >2 MB/s                                            |        |
| D.2  | Trigger transport migration BLE → Wi-Fi mid-chat                         | TRANSPORT_MIGRATE envelope sent; session state cycles through `migrating` → `established` |     |
| D.3  | Migration during media transfer                                          | No chunks lost; ACK sequence is contiguous                                              |        |
| D.4  | Both devices on cellular only (no LAN)                                   | Wi-Fi transport unavailable; system falls back to BLE                                   |        |

## Test cell E — QR bootstrap (FR-013, FR-044)

| Step | Action                                              | Expected                                                                          | Actual |
|------|-----------------------------------------------------|-----------------------------------------------------------------------------------|--------|
| E.1  | A taps "Show QR"                                    | Screen displays a QR encoding a HELLO + ephemeral pubkey                          |        |
| E.2  | B taps "Scan", points at A's screen                 | B parses HELLO; sends HELLO_ACK over BLE                                          |        |
| E.3  | E.1 with A's screen at low brightness (10%)         | Scan still succeeds within 5s                                                     |        |
| E.4  | E.1 with screen-protector film (worst-case)         | Scan still succeeds or surfaces a clear "scan failed, try again" error            |        |
| E.5  | Replay attack: capture a QR via phone camera, redisplay | B detects nonce mismatch, refuses to establish                                  |        |

## Test cell F — replay window + dedupe (FR-019, TR-007)

| Step | Action                                                                     | Expected                                                                       | Actual |
|------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------|--------|
| F.1  | Send 100 messages from A → B in rapid succession                            | B accepts all; sessionState stays `established`                                |        |
| F.2  | Replay message #50 to B (test build packet duplicator)                      | B's logs show `replayDropped` event; UI does not show the message twice        |        |
| F.3  | Send 65 messages, then re-send message #1                                  | After the 64-message window evicts #1, the replay is accepted (this is correct per spec §5) |   |

## Test cell G — signed config rollout (AC-001..004, LC-001..005)

| Step | Action                                                                          | Expected                                                                       | Actual |
|------|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------|--------|
| G.1  | Push a signed config with `vouchersEnabled: true`                                | Both devices' Vouchers tab appears within 60s                                  |        |
| G.2  | Push a signed config with an invalid signature (test config)                     | Both devices reject; logs show `signatureInvalid`; previous LKG is retained    |        |
| G.3  | Push a signed config with `interestPerHour: 999` (out of bounds)                 | Both devices reject at schema validation BEFORE signature check                |        |
| G.4  | Force-quit and relaunch                                                          | App boots with LKG config; behavior unchanged                                  |        |
| G.5  | Roll back: push the original signed config                                       | Both devices revert; UI changes accordingly                                    |        |

## Test cell H — billing (FR-053, FR-054, FR-055)

| Step | Action                                                                       | Expected                                                                                                | Actual |
|------|------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|--------|
| H.1  | iOS: tap "Lumos Pro" → Apple sandbox account → confirm                       | StoreKit returns success; entitlement appears in profile within 10s                                     |        |
| H.2  | Android: tap "Lumos Pro" → Play Billing test account → confirm               | Play Billing returns success; entitlement appears                                                        |        |
| H.3  | Cancel the iOS subscription via Settings                                     | App detects cancellation within one launch cycle; entitlement clears                                    |        |
| H.4  | Receipt-validation test: tamper with receipt locally (test build hook)       | App refuses entitlement; surfaces "verify subscription" prompt                                          |        |

## Test cell I — privacy + safety + consent (PC-001..PC-040, FR-060..FR-065)

| Step | Action                                                                     | Expected                                                                          | Actual |
|------|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------|--------|
| I.1  | Onboarding: dismiss "personalized ads" toggle                              | Ads remain context-only; no IDFA / AAID collected (verify via traffic capture)    |        |
| I.2  | Profile: set tags, then delete account                                     | DataStore wiped; no residual files in app sandbox                                |        |
| I.3  | Block a peer                                                                | Future INTEREST_REQUEST from that peer is auto-dropped without user notification  |        |
| I.4  | Report a peer                                                              | Local complaint file generated; surfaced to user as "we'll review"                |        |
| I.5  | Age-gate failure (under-18 entry)                                          | App refuses to advance past onboarding                                            |        |

## Test cell J — accessibility (UX-003, UX-004)

| Step | Action                                                  | Expected                                                       | Actual |
|------|---------------------------------------------------------|----------------------------------------------------------------|--------|
| J.1  | iOS: enable VoiceOver, navigate Discovery → Chat       | Every interactive element announces; focus order is sensible  |        |
| J.2  | Android: enable TalkBack, navigate same flow            | Same                                                           |        |
| J.3  | Dynamic Type: max font size                             | Layouts reflow; no truncation of critical buttons             |        |
| J.4  | Color-contrast scan with built-in iOS / Accessibility Scanner | All AA-required contrasts pass                          |        |
| J.5  | Right-to-left language pseudo-locale                    | Layout mirrors correctly                                       |        |

## Test cell K — soak / battery (NFR-003, NFR-005)

| Step | Action                                                                                | Expected                                                                                | Actual |
|------|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|--------|
| K.1  | Both devices visible-in-venue for 4 h, idle (no chats)                                  | Battery drain < 4% per hour per device; no thermal warnings                              |       |
| K.2  | Both devices in active chat, 1 message every 30s, for 1 h                               | Battery drain < 6% per hour per device                                                   |       |
| K.3  | Crash log: any uncaught exception                                                       | None expected; if seen, capture and file P0                                              |       |
| K.4  | Memory profile via Xcode Instruments / Android Profiler                                  | No leaks; resident memory < 150 MB; no heap growth over 4 h                              |       |

## Test cell L — crowded RF (TR-024, NFR-004)

| Step | Action                                                                            | Expected                                                                       | Actual |
|------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------|--------|
| L.1  | Run cell A inside a coffee shop with ≥10 BLE beacons in range                     | Discovery latency stays < 90s                                                  |        |
| L.2  | Run cell C inside the same environment                                            | Throughput drops gracefully; chunk ACK delivery success rate > 95%             |        |
| L.3  | Walk a 20m loop with the apps open                                                | App handles disconnect/reconnect cycles cleanly                                |        |

---

## Pass criteria

- **Every test cell** has all rows green for the build to be release-eligible.
- **One row red** in cells A–F → P0; block release.
- **One row red** in cells H–L → P1; convene release-readiness call.
- **One row red** in cell J → P2; can ship with a known-issue note IF security/privacy unaffected.

## Sign-off

| Role             | Name | Date | Signature |
|------------------|------|------|-----------|
| QA lead          |      |      |           |
| Engineering lead |      |      |           |
| Privacy reviewer |      |      |           |
| Release manager  |      |      |           |
