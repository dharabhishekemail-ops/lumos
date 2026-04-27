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
