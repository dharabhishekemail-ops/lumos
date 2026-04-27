set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

FIXTURES := "fixtures"
EVIDENCE_OUT := "evidence_bundle.zip"

help:
  @echo "just all | local-gates | python-test | android-test | ios-test | conformance | interop-sim | validate-config | evidence | clean"

all:
  make all

local-gates:
  make local-gates

python-test:
  make python-test

conformance:
  make conformance

interop-sim:
  make interop-sim

validate-config:
  make validate-config

android-test:
  cd android && ./gradlew testDebugUnitTest

ios-test:
  command -v xcodebuild >/dev/null 2>&1 || (echo "xcodebuild not found (run on macOS with Xcode installed)"; exit 1)
  mkdir -p build/ios
  cd ios/LumosApp && xcodebuild -project LumosApp.xcodeproj -scheme LumosApp -destination 'platform=iOS Simulator,name=iPhone 15' \
    -derivedDataPath ../../build/ios/DerivedData \
    test \
    -resultBundlePath ../../build/ios/TestResults.xcresult

evidence:
  make evidence

clean:
  make clean
