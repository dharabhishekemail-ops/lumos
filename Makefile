.PHONY: help all local-gates python-deps python-lock python-test interop-sim conformance validate-config android-test ios-test evidence rtm clean

FIXTURES     ?= fixtures
SCHEMAS      ?= schemas
EVIDENCE_OUT ?= evidence_bundle.zip
DOCS         ?= docs
PYTEST_ENV   ?= PYTEST_DISABLE_PLUGIN_AUTOLOAD=1

help:
	@echo "Lumos RC2.1 — local gates + platform targets"
	@echo
	@echo "Local gates (run anywhere with Python 3):"
	@echo "  make python-deps      # Verify required Python packages are installed"
	@echo "  make python-lock      # Write docs/python_dependency_lock.*"
	@echo "  make python-test      # Run pytest tests/ and write docs/pytest_report.xml"
	@echo "  make conformance      # Run fixture conformance -> docs/conformance_report.json"
	@echo "  make interop-sim      # Run Android↔iOS simulator -> docs/interop_simulator_report.json"
	@echo "  make validate-config  # Validate fixtures/config/config_01_valid.json"
	@echo "  make evidence         # Generate sanitized evidence_bundle.zip"
	@echo "  make all              # Run all local gates + evidence bundle"
	@echo
	@echo "Platform-runner gated:"
	@echo "  make android-test     # ./android/gradlew testDebugUnitTest (needs Android SDK)"
	@echo "  make ios-test         # xcodebuild test (needs macOS + Xcode)"
	@echo
	@echo "Vars: FIXTURES=$(FIXTURES) SCHEMAS=$(SCHEMAS) EVIDENCE_OUT=$(EVIDENCE_OUT)"

all: local-gates evidence

local-gates: python-deps python-lock python-test conformance interop-sim validate-config

python-deps:
	@python3 tools/check_python_deps.py

python-lock:
	@python3 tools/generate_dependency_lock.py

python-test: python-deps
	@$(PYTEST_ENV) python3 -m pytest tests/ -v --junitxml=$(DOCS)/pytest_report.xml

interop-sim:
	@python3 tools/interop_simulator.py

conformance:
	@python3 tools/conformance_runner.py --fixtures $(FIXTURES) --schemas $(SCHEMAS) --out $(DOCS)/conformance_report.json

validate-config:
	@python3 tools/validate_config.py --config $(FIXTURES)/config/config_01_valid.json

android-test:
	@cd android && ./gradlew testDebugUnitTest

ios-test:
	@command -v xcodebuild >/dev/null 2>&1 || (echo "xcodebuild not found (run on macOS with Xcode installed)"; exit 1)
	@mkdir -p build/ios
	@cd ios/LumosApp && xcodebuild -project LumosApp.xcodeproj -scheme LumosApp \
		-destination 'platform=iOS Simulator,name=iPhone 15' \
		-derivedDataPath ../../build/ios/DerivedData \
		test -resultBundlePath ../../build/ios/TestResults.xcresult

evidence: python-lock
	@python3 tools/generate_evidence_bundle.py --fixtures $(FIXTURES) --out $(EVIDENCE_OUT)

rtm:
	@echo "RTM is hand-curated in docs/RTM.md after the rebuild."
	@echo "If you need a JSON view, see docs/RTM_COVERAGE_REPORT.json."

clean:
	@rm -f $(EVIDENCE_OUT) $(DOCS)/conformance_report.json $(DOCS)/interop_simulator_report.json $(DOCS)/pytest_report.xml $(DOCS)/python_dependency_lock.json $(DOCS)/python_dependency_lock.txt
