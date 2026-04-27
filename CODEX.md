# CODEX.md — Lumos Engineering Instructions

## Source of truth

This repository is based on RC2.1 and replaces all previous RC1 artifacts.

The following documents are normative:
- docs/RTM.md
- docs/ON_DEVICE_TEST_PLAN.md
- docs/ROLLOUT_PLAN.md
- docs/INCIDENT_RUNBOOK.md
- docs/PRODUCTION_READINESS_PLAN.md
- docs/TEST_REPORT.md
- schemas/protocol-envelope.schema.json
- schemas/signed-config.schema.json

## Non-negotiable rules

1. Do not change the protocol wire format unless:
   - schemas/protocol-envelope.schema.json is updated,
   - tools/canonical_codec.py is updated,
   - Android and iOS codecs are updated,
   - golden and negative fixtures are regenerated,
   - conformance and interop simulator pass.

2. Never edit Android/iOS protocol codecs without adding or updating fixtures.

3. Any change must preserve:
   - make all
   - make conformance
   - make interop-sim
   - make validate-config
   - make evidence

4. No plaintext PII in logs.
5. No force unwrap / `!!` / `precondition` / `fatalError` in peer-input/config/network paths.
6. All remote-config behavior must validate schema, signature, bounds, and retain Last-Known-Good on failure.
7. Any requirement moved from Partial/Not Covered to Covered must include executable tests and evidence.

## Required PR format

Every PR must include:
- Summary
- Requirements impacted
- Files changed
- Tests run
- Evidence generated
- Risk / rollback note
