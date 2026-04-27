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
