# Phase 1 Traceability Mapping

This document maps the generated Phase 1 scaffolding to the approved Lumos document set.

## Normative Inputs
- Lumos SRS v1.0 Baseline
- Lumos System Architecture Specification v1.0
- Lumos Mobile Detailed Design Spec Android+iOS v1.0
- Lumos Protocol & Interop Contract v1.0
- Lumos Crypto Protocol Spec v1.0
- Lumos Signed Config Schema & Governance v1.0
- Lumos VVMP v1.0
- Lumos Edge Case Catalogue & Failure Mode Tests v1.0
- Lumos Threat Model & Abuse Case Analysis v1.0
- Lumos Interop Test Spec & Fixtures v1.0

## Phase 1 Deliverables and Document Linkage

### 1. Shared Protocol Models + Fixtures
- Implements contract-first envelope/message headers and capability negotiation DTOs.
- Supports versioning and message type dispatch scaffolding.
- Fixtures included for happy path and negative test placeholders.
- Source: Protocol & Interop Contract, Interop Test Spec & Fixtures, VVMP.

### 2. Signed Config Runtime Skeleton
- Bounded config loading lifecycle:
  - parse -> schema/basic validation -> verify signature (interface) -> apply atomically -> keep last-known-good
- Explicit non-remotely-configurable invariants placeholders.
- Source: Signed Config Schema & Governance, Threat Model, DDS.

### 3. Crypto Wrapper Interfaces
- Explicit interfaces for key management, session handshake, AEAD, HKDF, ratchet/session state.
- No direct UI/module dependency on crypto implementation.
- Source: Crypto Protocol Spec + test vectors, Threat Model, SAS/DDS.

### 4. Session State Machine Scaffolding
- Transport-agnostic orchestration states/events/reducer with transition guards.
- Duplicate suppression and retry hooks are placeholders.
- Source: SAS state machine sections, DDS session orchestrator design, Edge Case Catalogue, VVMP.

### 5. Android Project Skeleton (Modular)
- app, core-common, core-protocol, core-config, core-crypto-api, feature-session
- Compose + ViewModel/UDF-ready architecture.
- Source: DDS Android sections, SAS, SRS UX/NFR + diagnostics requirements.

### 6. iOS Project Skeleton (SwiftPM modules)
- Common contracts, protocol codec stubs, config runtime, crypto API, session store/reducer.
- SwiftUI + Swift Concurrency ready.
- Source: DDS iOS sections, SAS, Protocol/Crypto/Config docs.

## Phase 1 Exit Criteria (to continue coding)
- Protocol codec tests pass against approved fixtures.
- Crypto API adapters pass spec test vectors (once implemented).
- Config runtime rejects malformed/unsigned/unsafe configs (per governance).
- Session reducer transition tests cover critical state transitions.
- Android/iOS skeletons build and run basic boot flow.
