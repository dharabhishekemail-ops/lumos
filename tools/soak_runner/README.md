# Soak / Fault Injection Runner (Phase 9)

This directory contains orchestration scripts and guidance to run:
- deterministic fault profiles (loss/reorder/dup/latency)
- long-running chat/media sessions
- transport migration drills
- evidence bundle export validation

In a full build, these runners should drive:
- Android instrumentation tests (connected device farm)
- iOS XCTest plans (simulator + device)

## Fault profiles
See tools/fault_profiles/*.json
