# CI Gates (Phase 9)

These gates are derived from VVMP + Edge Case Catalogue + Threat Model:
- **Protocol/codec conformance** must pass on both platforms.
- **Interop fixtures** must decode/encode identically (golden).
- **Transport migration** tests must pass with no duplicate visible messages.
- **Fault injection soak** must pass for at least 30 minutes per profile with:
  - no crashes
  - no unbounded memory growth
  - bounded retry budgets
  - no UI deadlocks / ANRs
- **Config LKG + rollback drill** must pass.
- **Privacy redaction** must be validated on exported evidence bundle.

## Required build steps
Android:
- unit tests: `./gradlew test`
- instrumentation: `./gradlew connectedAndroidTest`
- lint: `./gradlew lint`
- baseline profile (optional, recommended): `./gradlew :app:generateBaselineProfile`

iOS:
- unit tests: `xcodebuild test -scheme LumosApp -destination 'platform=iOS Simulator,name=iPhone 15'`
- swiftlint (recommended)

## Release blockers
- Any failing gate blocks release candidate.
- Any change affecting Protocol/Crypto/Config requires conformance rerun.
