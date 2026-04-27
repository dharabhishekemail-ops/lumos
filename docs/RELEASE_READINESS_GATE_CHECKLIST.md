# Release Readiness Gate Checklist (RRGC) – Lumos v1.0

This checklist is intended to be used for each Release Candidate (RC) and Pilot build.

## A. Build Integrity (Must Pass)
- [ ] Android: clean build from scratch (./gradlew clean :app:assembleRelease)
- [ ] iOS: clean archive with export using ExportOptions.plist template
- [ ] Reproducible versioning: build number increments; git commit hash embedded
- [ ] No debug endpoints or developer toggles enabled in production builds
- [ ] Crash-free smoke test: launch → onboarding → discovery → request → chat

## B. Security & Privacy (Must Pass)
- [ ] Signed config verification enabled and enforced
- [ ] Config bounds validation enforced; unsafe config rejected
- [ ] Last Known Good (LKG) config fallback works offline
- [ ] Operator rollback works and is logged
- [ ] No PII in logs; diagnostics bundle is redacted
- [ ] E2E crypto sanity checks: AEAD auth failure → safe discard (no crash)

## C. Functional Critical Path (Must Pass)
- [ ] Onboarding: age gate and consent flows
- [ ] Advertise/Discover: visible on/off; timeout works; pause works
- [ ] Interest: send/receive/accept/reject/ignore/block
- [ ] Mutual accept gate: no chat/media pre-accept
- [ ] Chat text: send/ack/retry/dedupe (no duplicates after migration)
- [ ] Media: encrypted chunk transfer; integrity verification; resume/retry
- [ ] Safety: block/report/mute; safety center reachable
- [ ] Diagnostics: export sanitized debug bundle

## D. Edge Case / Resilience (Must Pass)
- [ ] Permission revoke while active session → graceful degradation
- [ ] Background/foreground transitions during scan/chat/media
- [ ] Low power mode / battery saver behavior does not crash; shows user-friendly status
- [ ] Fault injection profiles: loss/dup/reorder/latency – no deadlocks, no UI freeze
- [ ] Corrupted packets/media chunk → safe fail (discard + status)

## E. Monetization & Venue Ops (Must Pass if enabled)
- [ ] Billing purchase flow works (sandbox)
- [ ] Restore purchases works offline fallback to receipt cache
- [ ] Ads/Promotions tab does not block discovery/chat
- [ ] Voucher scan parsing + signature validation flow (if enabled)
- [ ] Config-driven SKU visibility labels render correctly

## F. Performance (Targets)
- [ ] Cold start < 2.5s on mid-tier device (target)
- [ ] Discovery UI updates smooth; no flicker
- [ ] Chat send UI non-blocking; no jank during media transfer
- [ ] Battery consumption within session budget (see PERF_BUDGET.md)

## G. Store Compliance (Must Pass)
- [ ] Privacy Policy URL, Terms of Use accessible
- [ ] Data safety / nutrition labels prepared
- [ ] Permission rationale strings verified
- [ ] Age rating / dating category compliance (store metadata)
- [ ] No “hidden” gating or deceptive purchase prompts

## Evidence Pack Output (Mandatory)
Attach:
- Build info (version, commit hash, config hash)
- Automated test report
- Interop fixture pass log
- Fault-injection pass log
- Diagnostics bundle sample (sanitized)
