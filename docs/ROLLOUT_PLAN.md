# Lumos Phased Rollout Plan

**Document version:** 1.0
**Target audience:** Release manager, on-call SRE, product lead

This document is the rollout playbook for shipping Lumos to production. The signed-config system is the rollout mechanism: every dial — feature flags, rate limits, billing config, even the legal-copy version — is governed by a remote-signed pack and applied atomically with last-known-good fallback. That means rollout is not "ship a new APK and pray," it's "ship the binary once with everything off, then turn things on remotely as confidence builds."

## Phase 0 — pre-flight (T-7 days)

Before anyone outside the company sees the app:

1. **Conformance gate green.** `make all` returns 0 on the release-candidate commit.
2. **On-device test plan complete.** All cells in `docs/ON_DEVICE_TEST_PLAN.md` signed off.
3. **Security review complete.** Threat model walked through with security; Keychain/Keystore pen-test sign-off captured.
4. **Privacy review complete.** Legal copy v1 baked into `signedConfig.payload.legalCopyVersion`; data-flow diagram approved.
5. **Crash reporting wired.** Sentry / Firebase Crashlytics SDK integrated and verified by deliberately crashing in a debug build.
6. **Telemetry firehose.** Counters defined per `docs/PERF_BUDGET.md` are emitting on a private endpoint.
7. **Kill switch verified.** Pushing a signed config with `featureFlags.adsTabEnabled=false` (or any other flag) flips behavior on a dev device within 60s.

If any of those is red, do not proceed.

## Phase 1 — internal alpha (T-0 to T+5 days)

**Audience:** ~20 employees, hand-selected, opt-in.
**Distribution:** TestFlight (iOS), Play Internal Test track (Android).
**Signed config baseline:**

```json
{
  "featureFlags": {
    "adsTabEnabled": false,
    "vouchersEnabled": false,
    "operatorModeEnabled": false,
    "diagnosticsPanelEnabled": true
  },
  "rateLimits": { "interestPerHour": 30, "messagesPerMinute": 30 },
  "rolloutPercent": 100
}
```

**What this means:** core chat works, ads tab hidden, vouchers hidden, ops panel visible to alpha testers.

**Daily checklist:**
- Crash-free sessions ≥ 99.5%
- Battery telemetry: median drain in venue mode < 4 %/h
- Replay-detection events: ≤ 1 per 1,000 messages (above this is suspicious)
- Discovery latency p50: < 30s
- "Report user" tickets: triage daily

**Exit criteria for Phase 1:**
- ≥ 80 % of alphas have completed at least one chat
- Zero P0 incidents
- ≤ 3 P1 incidents, all triaged
- Crash-free rate has held ≥ 99.5 % for ≥ 72 h

## Phase 2 — closed beta (T+5 to T+14 days)

**Audience:** ~500 invited testers per platform, recruited via in-app waitlist.
**Distribution:** TestFlight + Play Closed Test.
**Signed config:** unchanged from Phase 1 (still no monetization, no vouchers).

**Daily checklist:** as Phase 1, plus:
- Wire-format error rate ≤ 1 / 10,000 messages (any higher means the on-device codec drifted from canonical — investigate immediately, this is the gate the rebuild was designed to protect)
- Negotiation failure modes (`incompatibleVersion`, `noCommonTransport`) ≤ 0.5 % of session attempts
- Signed-config apply success rate ≥ 99.9 % (the < 0.1 % failures should all be schema-rejected, not signature-rejected)

**Exit criteria for Phase 2:**
- All daily checklist items green for ≥ 7 consecutive days
- At least 50 cross-platform pairs have completed a chat (Android↔iOS specifically)

## Phase 3 — open beta with monetization off (T+14 to T+21 days)

**Audience:** App Store / Play Store public listing, but heavily caveated as "early access."
**Distribution:** Public store, no marketing push.
**Signed config:**

```json
{
  "featureFlags": {
    "adsTabEnabled": false,
    "vouchersEnabled": true,            // ← enabled
    "operatorModeEnabled": false,
    "diagnosticsPanelEnabled": false   // ← hidden from public
  },
  "rateLimits": { "interestPerHour": 30, "messagesPerMinute": 60 }
}
```

**Per-region rollout:** New Zealand → Singapore → Australia → United Kingdom → United States. 24-hour soak between regions.

## Phase 4 — open with monetization (T+21 onward)

**Audience:** Public.
**Signed config:**

```json
{
  "featureFlags": {
    "adsTabEnabled": true,
    "vouchersEnabled": true,
    "operatorModeEnabled": false,
    "diagnosticsPanelEnabled": false
  }
}
```

**Phased per-region:** roll the same way as Phase 3.

## Kill switch

Any of the following, anywhere in the rollout, triggers an immediate kill switch:

| Trigger                                                                      | Action                                                                       |
|------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Crash-free rate drops below 99 % for 1 h                                     | Push signed config with `adsTabEnabled=false vouchersEnabled=false`          |
| Wire-format error rate > 1 / 1,000 messages for 15 min                        | Push signed config with `messagesPerMinute=0` (force quiet)                  |
| Signed-config signature failures > 0.1 % over 1 h                            | Pause new config rollouts; investigate — may indicate a compromised key      |
| Privacy / safety incident reported                                           | Push signed config with `vouchersEnabled=false`; convene incident call       |
| Billing fraud spike                                                          | Push signed config disabling SKUs; refund affected users                     |

**The kill switch is a signed config push.** It takes effect within ~60s on every device that has working network. A device that's offline will hit the killed state on its next config check (every 6 h or on app launch).

## Rollback drill (run quarterly)

Once a quarter, on a non-prod environment:

1. Pick a random feature flag.
2. Push a config that breaks it (e.g., `interestPerHour: 9999` — out of bounds).
3. Verify all devices reject the bad config and stay on the prior LKG.
4. Push a corrected config.
5. Verify all devices apply within 60s.
6. Capture timing in `docs/Evidence_Bundle.md`.

## Communication plan

| Audience            | Channel                          | Cadence                                   |
|---------------------|----------------------------------|-------------------------------------------|
| Internal eng        | #lumos-rollout in Slack          | Real-time during phases, daily otherwise  |
| Privacy reviewer    | Email summary                    | End of each phase                         |
| Customer support    | Wiki page + on-call rotation     | Updated at every phase boundary           |
| External press      | Embargoed brief (Phase 4 only)   | One week before Phase 4 launch            |

## Out-of-scope (intentionally not in this rollout)

- **Web client.** No browser surface in v1.
- **Server-side chat history.** Lumos is local-only; there is no central message store to roll out.
- **Federation with other apps.** Single closed protocol in v1.
