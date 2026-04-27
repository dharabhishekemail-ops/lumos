# Evidence Bundle (Sanitized) – Phase 9

The app exports a **sanitized debug bundle** (ZIP) for field validation.
It must include:
- app build version, protocol version, config version hash
- transport timeline (no PII)
- orchestrator state transitions (no PII)
- counters: retries, drops, dedupe hits, migrations
- crash-free session stats
- redacted logs (no message content, no identifiers beyond ephemeral, rotated ids)
- optional: perf snapshots (frame time buckets, battery estimates)

This bundle is crucial for pilot support without requiring code changes.
