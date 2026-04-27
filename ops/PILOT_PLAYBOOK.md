# Pilot Playbook – Venue Trial (Lumos)

## Objectives
- Validate discovery reliability in RF-noisy venues
- Validate UX clarity and safety flows
- Validate operator config rollout/rollback process
- Collect diagnostics bundles for failures

## Pre-Pilot Setup
1. Generate signed config:
   - enable pilot flags
   - set conservative rate limits
   - set lower duty cycle initially
2. Publish config to pilot endpoint (CDN/Git-hosted)
3. Verify Operator Mode:
   - fetch config
   - apply config
   - verify active config hash and timestamp
4. Prepare test devices:
   - Android API 26, 31, 34
   - iOS 15, 16, 17

## Venue Execution Script
1. Arrive and record RF conditions:
   - Wi-Fi saturation notes
   - crowd density
2. Run standard scenario:
   - two devices advertise
   - two devices discover
   - interest → accept
   - chat exchange
   - send a photo (medium size)
3. Run edge scenario:
   - toggle airplane mode on one device mid-chat
   - revoke bluetooth permission mid-session (Android/iOS)
   - background app during media transfer
4. Collect evidence:
   - export diagnostics bundle from both devices
   - note timestamps and steps

## Success Criteria
- No crashes
- No duplicate messages after migration
- Media integrity verified
- Clear user messaging on degraded states

## Post-Pilot
- Review diagnostics
- Apply config-only tuning first (duty cycle, retry budgets, UI copy)
- Code changes only if absolutely necessary (CR process)
