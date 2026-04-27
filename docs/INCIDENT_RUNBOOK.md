# Lumos Incident Response Runbook

**Document version:** 1.0
**Audience:** On-call engineers, incident commander
**Companion docs:** `docs/ON_DEVICE_TEST_PLAN.md`, `docs/ROLLOUT_PLAN.md`

This runbook lists the signals you'll see in production, what they mean, and the action to take. Severity levels follow VVMP §5: **P0** = active user harm or data exposure (page immediately); **P1** = degraded experience for many users (24-hour SLA); **P2** = degraded experience for some users (72-hour SLA).

## Triage tree

```
Telemetry alarm fires
  ├── Is user data being exposed? → P0 — see "Data exposure" below
  ├── Is the app crashing > 1% of sessions? → P0 — see "Crash spike"
  ├── Is the wire-format error rate > 1 / 1,000? → P0 — see "Codec drift"
  ├── Are signed configs failing to apply? → P1 — see "Config rejection"
  ├── Is billing failing? → P1 — see "Billing failure"
  ├── Is discovery latency > 90s? → P2 — see "Discovery slow"
  └── Is battery drain > 8%/hour? → P2 — see "Battery regression"
```

---

## P0: Data exposure

**Symptom.** A user reports that profile data they didn't share is visible to a peer; OR telemetry shows a profile field broadcast outside the consented set; OR a packet capture shows plaintext where ciphertext is expected.

**Immediate action (within 5 min):**
1. Push a signed config with `vouchersEnabled=false` and `adsTabEnabled=false` to reduce surface area.
2. If the leak is in the protocol layer (not just UI), push `messagesPerMinute=0` to mute new sends.
3. Page security; convene incident call.
4. Snapshot logs for the suspect time window from telemetry.

**Investigation:**
- Confirm the suspect envelope on the wire. Is the field actually present, or is it a UI bug?
- Reproduce locally with the canonical codec.
- If the field IS in the envelope: this is a protocol-layer regression. Check the codec diff between the affected build and the previous green one.
- If the field is NOT in the envelope: this is a UI bug. Patch and ship via app update.

**Recovery:**
- Patch + new RC + on-device test plan re-run + rollout from Phase 1 again.
- Consult Privacy reviewer on whether external notification is required.

## P0: Crash spike

**Symptom.** Crash-free rate drops below 99 % over a 1-hour window; OR Crashlytics / Sentry shows a sudden spike in a specific stack.

**Immediate action:**
1. Identify the dominant stack from telemetry.
2. If it's in `LumosKit` / `core-protocol` / `core-session` / `core-config` → push a signed config that disables the implicated feature flag.
3. If it's in a non-essential feature (ads, vouchers, operator mode) → push a signed config with that flag off.
4. If it's in a core path (chat, discovery) and config can't disable it → consider an emergency app-store rollback.

**Investigation:**
- Match the stack to recent commits (last 2 weeks).
- Reproduce locally.
- If the crash is in the codec: run `make all` on the affected build's commit. The fact that it shipped means the gate didn't catch this; that's its own incident.

**Recovery:**
- Patch + new RC + soak in alpha → beta → public.

## P0: Codec drift (wire-format error rate spike)

**Symptom.** Telemetry counter `wire_format_decode_errors` exceeds 1 / 1,000 messages over 15 min.

**This is the failure mode the rebuild was designed to detect.** It means an Android peer and an iOS peer are producing bytes that aren't decoding cleanly on the other side. Likely cause: a code change to `ProtocolCodec.swift` or `ProtocolCodec.kt` that diverged from the canonical reference.

**Immediate action:**
1. Push a signed config with `messagesPerMinute=10` (slow it down) to reduce blast radius.
2. Page protocol owner.

**Investigation:**
- Pull a sample of the failing envelopes from telemetry (in production builds these should be present in error events as the first 200 bytes only — never the full envelope, never the ciphertext).
- Run them through `tools/canonical_codec.py decode` and `tools/conformance_runner.py`.
- Check the codec diff between the regression build and the prior green one.
- If the divergence is real: revert the codec change. Re-run `make all`.

**Recovery:**
- Patch + new RC + Phase 1.
- Add a fixture covering this case to `fixtures/golden/` so the gate would have caught it next time.

## P1: Config rejection rate spike

**Symptom.** Telemetry counter `signed_config_apply_failures` exceeds 0.1 % over 1 hour.

**Causes (in order of likelihood):**
1. **Signing key rotation gone wrong** — the new pack was signed with a key the deployed apps don't trust yet.
2. **Schema regression** — the new pack has a field the deployed schema rejects.
3. **Signature service compromise** — invalid signatures are being signed by an attacker.

**Immediate action:**
1. Pause further config pushes.
2. Inspect the failing pack with `python3 tools/validate_config.py`. Does it pass schema?
3. If yes: re-sign with the production key, verify signature locally, then attempt re-push.
4. If no: revert to the previous good pack (push that one again).
5. If signing service is suspect: convene security; rotate keys per the rotation runbook (out of scope here).

## P1: Billing failure

**Symptom.** Purchases are not granting entitlement, or refunds are not clearing it.

**Action:**
1. Check Apple StoreKit / Google Play Billing dashboards for service status.
2. If service-side: file a support ticket; communicate to users via in-app banner.
3. If app-side: tail receipt-verification logs. Are signatures verifying? Are receipts being parsed?
4. Push a signed config with the affected SKU disabled to stop further failed purchases.

## P2: Discovery slow

**Symptom.** Discovery latency p50 exceeds 60s; p95 exceeds 90s.

**Likely causes:**
- Crowded RF environment (test cell L showed this is graceful)
- BLE chip on a specific phone model misbehaving (search Crashlytics for device-model correlation)
- Dedupe/filtering bug causing valid peers to be hidden

**Action:**
- File P2; investigate during business hours.
- Consider raising `transport.backoffMaxMs` via signed config to reduce contention.

## P2: Battery regression

**Symptom.** Median battery drain exceeds 6 %/hour in venue mode.

**Action:**
- Profile a representative device with platform tooling (Xcode Instruments / Android Studio Profiler).
- Likely culprit: BLE scan duty cycle too aggressive, or session orchestrator polling instead of waiting on events.
- Patch + measure + ship.

---

## Postmortem template

Every P0 and every recurring P1 gets a postmortem (doc filed in `docs/postmortems/`). Sections:

1. **What happened** (timeline, in PT or UTC consistently)
2. **Impact** (number of users, duration)
3. **Root cause** (technical, not "we should test more")
4. **What we did right**
5. **What we did wrong**
6. **Action items** with owners and deadlines
7. **Prevention**: what test would have caught this? Add it.

## On-call rotation expectations

- Primary on-call: respond within 5 min for P0, 15 min for P1.
- Secondary: shadow primary; cover when primary is unreachable.
- Hand-off Mondays at 10 AM in the on-call's local timezone.
- Hand-off doc lives in `ops/oncall_handoff.md` and is updated by the outgoing primary.

## Known gotchas

- **Signed config TTL.** Apps cache configs for 6 hours. A kill-switch push reaches most users within ~60s if their app is in the foreground; backgrounded apps may take up to 6 h. For true emergencies, force-quit notification is unavailable; this is a known limitation.
- **BLE scan throttling.** Both iOS and Android throttle BLE scans aggressively when the app is backgrounded. This is intentional (battery) and not a bug.
- **iOS local network permission prompt.** First Wi-Fi-Aware-style use triggers a system permission prompt. Some users dismiss this and never see local discovery work; we should surface a friendly retry in the UI.
- **Replay-window false positives.** If a user sends 65+ rapid messages and one of the early ones is naturally retransmitted by the radio layer, the dedupe window may evict it before the retransmit. The protocol handles this correctly (the retransmit becomes a new accepted message), but log analysts may misread the logs.
