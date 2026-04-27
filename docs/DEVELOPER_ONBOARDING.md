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
# Developer Onboarding

## Android

### Prerequisites
- JDK 17
- Android SDK with API 35 platform installed
- Network access for Gradle dependency resolution

### Module layout
The Android project is rooted at `android/` and uses a multi-module Gradle setup.
The following modules are included in `android/settings.gradle.kts` and currently participate in local builds:

- `:app`
- Core modules: `:core-billing`, `:core-common`, `:core-config`, `:core-crypto-api`, `:core-designsystem`, `:core-media`, `:core-protocol`, `:core-remoteconfig`, `:core-session`, `:core-transport-mux`
- Feature modules: `:feature-ads`, `:feature-chat`, `:feature-diagnostics`, `:feature-discovery`, `:feature-onboarding`, `:feature-operator`, `:feature-profile`, `:feature-requests`, `:feature-safety`, `:feature-session`, `:feature-voucher`

### Build and test
From repository root:

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

If `./gradlew` fails from repository root, run it from `android/` because the Gradle wrapper is located there.
This guide covers local setup for the Lumos RC2.1 repository and a reproducible iOS bring-up flow.

## iOS (XcodeGen + XCTest)

### Prerequisites (macOS)

- Xcode 15+
- Command line tools (`xcode-select --install`)
- Homebrew

Install required CLI tools:

```bash
brew update
brew install xcodegen xcbeautify
```

### Generate the iOS project

From repository root:

```bash
cd ios/LumosApp
xcodegen generate
```

Expected output summary:

- `⚙️  Generating project...`
- `⚙️  Writing project...`
- `Created project at .../ios/LumosApp/LumosApp.xcodeproj`

### Run LumosApp integration tests (XCTest)

```bash
cd ios/LumosApp
xcodebuild \
  -project LumosApp.xcodeproj \
  -scheme LumosApp \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  clean test | xcbeautify
```

Expected output summary:

- Build phases complete without errors.
- `Test Suite 'All tests' ... passed`
- `** TEST SUCCEEDED **`

### Run LumosKit package tests via xcodebuild

```bash
cd ios/LumosKit
xcodebuild \
  -scheme LumosKit-Package \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  clean test | xcbeautify
```

Expected output summary:

- SwiftPM targets compile successfully.
- LumosKit test bundles execute.
- `** TEST SUCCEEDED **`

### Linux note

`swift test` for `ios/LumosKit` is not a substitute for the macOS gating path because this package uses `CryptoKit`; run the iOS acceptance commands above on macOS.
