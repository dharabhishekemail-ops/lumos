# AGENTS.md — Lumos Codex Instructions

## Source of truth

RC2.1 is the only valid source of truth. Do not resurrect RC1 code.

Normative documents:
- docs/RTM.md
- docs/ON_DEVICE_TEST_PLAN.md
- docs/ROLLOUT_PLAN.md
- docs/INCIDENT_RUNBOOK.md
- docs/PRODUCTION_READINESS_PLAN.md
- docs/TEST_REPORT.md
- schemas/protocol-envelope.schema.json
- schemas/signed-config.schema.json

## Non-negotiable engineering rules

1. Do not change protocol wire format unless all of these are updated together:
   - schemas/protocol-envelope.schema.json
   - tools/canonical_codec.py
   - Android ProtocolCodec.kt
   - iOS ProtocolCodec.swift
   - fixtures/golden/*
   - fixtures/negative/*
   - conformance report
   - interop simulator report

2. Every PR must preserve:
   - make all
   - make conformance
   - make interop-sim
   - make validate-config
   - make evidence

3. No unsafe logging:
   - no plaintext profile data
   - no ciphertext dumps
   - no private keys
   - no full packet dumps in production logs

4. No crash-prone constructs in peer/config/network input paths:
   - no Kotlin `!!`
   - no Swift force unwraps
   - no `fatalError`
   - no `precondition`
   - no unhandled `require` on remote input

5. Signed config must always:
   - validate schema
   - validate signature
   - validate bounds
   - reject unsafe values
   - retain Last-Known-Good on failure

6. A requirement can move to Covered in RTM only when:
   - code exists
   - test exists
   - evidence exists
   - RTM is updated
   - CI artifact proves it

## Required PR format

Every PR must include:

- Summary
- Requirements impacted
- Files changed
- Tests run
- Evidence generated
- Risk
- Rollback plan
