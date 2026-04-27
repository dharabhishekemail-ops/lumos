# Lumos Developer Onboarding (RC2.1)

This onboarding guide is for engineers working on the RC2.1 baseline.

## 1) Source of truth and guardrails

Use RC2.1 artifacts as the only source of truth:

- `docs/RTM.md`
- `docs/ON_DEVICE_TEST_PLAN.md`
- `docs/ROLLOUT_PLAN.md`
- `docs/INCIDENT_RUNBOOK.md`
- `docs/PRODUCTION_READINESS_PLAN.md`
- `docs/TEST_REPORT.md`
- `schemas/protocol-envelope.schema.json`
- `schemas/signed-config.schema.json`

Before touching protocol/config behavior, review the non-negotiable constraints in `AGENTS.md`.

## 2) Python gates (required local baseline)

Install dependencies (venv recommended), then run the local gates.

```bash
python3 -m pip install -r tools/requirements.txt
make python-deps
make python-lock
make python-test
make conformance
make interop-sim
make validate-config
make evidence
```

Fast path:

```bash
make all
```

What each gate produces:

- `make python-lock` → `docs/python_dependency_lock.json`, `docs/python_dependency_lock.txt`
- `make python-test` → `docs/pytest_report.xml`
- `make conformance` → `docs/conformance_report.json`
- `make interop-sim` → `docs/interop_simulator_report.json`
- `make evidence` → `evidence_bundle.zip` and `docs/evidence_summary.md`

## 3) iOS setup and test commands

Requirements:

- macOS with Xcode installed
- iOS Simulator runtime including iPhone 15

Commands:

```bash
# Generate/update project file when needed
cd ios/LumosApp && xcodegen generate

# Run the Makefile gate
make ios-test
```

`make ios-test` runs `xcodebuild test` with:

- project: `ios/LumosApp/LumosApp.xcodeproj`
- scheme: `LumosApp`
- destination: `platform=iOS Simulator,name=iPhone 15`
- derived data: `build/ios/DerivedData`
- result bundle: `build/ios/TestResults.xcresult`

## 4) Android setup and test commands

Requirements:

- Android SDK installed and available to Gradle

Commands:

```bash
# Run the Makefile unit-test gate
make android-test

# Optional release-readiness checks from CI gates docs
cd android
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
./gradlew :app:generateBaselineProfile
./gradlew assembleRelease
```

## 5) Evidence bundle generation

Generate sanitized evidence artifacts with:

```bash
make evidence
```

This packages evidence into `evidence_bundle.zip` and depends on fresh dependency lock output. Include this bundle (or its referenced artifacts) in release evidence and PR validation notes.

## 6) RTM update rules

`docs/RTM.md` is hand-curated.

A requirement may move to **Covered** only when all are true:

1. Code exists.
2. Test exists.
3. Evidence exists.
4. RTM row is updated.
5. CI artifact proves it.

When coverage changes:

- update the specific RTM rows with concrete implementation/test pointers
- regenerate and attach relevant evidence (`make all`, `make evidence`)
- keep statuses honest (`Covered`, `Partial`, `Not Covered`, `Not in scope`)

## 7) PR workflow

Follow `.github/pull_request_template.md` and include these sections:

- Summary
- Requirements impacted
- Files changed
- Tests run
- Evidence generated
- Risk
- Rollback plan

Minimum command set expected before opening/merging protocol/config-sensitive PRs:

```bash
make all
make conformance
make interop-sim
make validate-config
make evidence
```

Platform-gated validation (run by platform engineers/toolchains):

```bash
make ios-test
make android-test
```

Additional expectations:

- Do not change protocol wire format unless all linked schemas/codecs/fixtures/reports are updated together.
- Do not log plaintext profile data, ciphertext dumps, private keys, or full packet dumps in production logs.
- Signed config handling must continue to validate schema, signature, and bounds, reject unsafe values, and retain Last-Known-Good on failure.
