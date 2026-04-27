# Lumos RC1 Mono-root Workspace

Layout:
- /android  : Android Studio root (Gradle)
- /ios      : iOS app + SPM package(s)
- /schemas  : signed config + protocol schemas
- /fixtures : protocol/inter-op fixtures (conformance runner input)
- /shared-fixtures : additional cross-platform fixtures
- /tools    : conformance runner + evidence bundle generator

Quick commands:
- make android-test
- make ios-test        (macOS + Xcode required)
- make conformance
- make evidence

The evidence bundle is a sanitized zip containing:
- metadata (tool versions, timestamps)
- conformance runner output
- optional test reports if present
- checksums for schemas/fixtures
