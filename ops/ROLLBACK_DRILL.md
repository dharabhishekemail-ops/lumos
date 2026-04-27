# Operator Rollback Drill – Lumos (Config LKG)

Goal: prove that a bad config can be rolled back **without app update**.

## Drill Setup
- Prepare config A (known-good)
- Prepare config B (intentionally problematic but still signature-valid), e.g.:
  - retry budget too high (should be rejected by bounds)
  - disable non-core feature (ads tab) allowed
- Publish both to pilot endpoint

## Steps
1. Apply config A via Operator Mode
   - verify active hash = A
2. Attempt apply config B
   - Expected:
     - If bounds invalid: reject and keep A
     - If valid but undesirable: apply B
3. Trigger rollback
   - select LKG A and apply rollback
4. Verify:
   - active hash returns to A
   - session/discovery continues operating
   - rollback event recorded in diagnostics bundle

## Evidence
- Screenshots of hashes and timestamps
- Exported diagnostics bundle showing:
  - apply attempt
  - reject reason or apply
  - rollback event
