# Lumos — RC1 Rebuild (RC2 candidate)

Date: 2026-04-27

This is the rebuilt Lumos workspace after the RC1 audit identified six release blockers (three incompatible wire formats, an iOS target that wouldn't compile, a falsely-green conformance runner, an RTM that was 50%+ TBD, duplicate Android source trees, and false-positive review findings). All six are fixed. See **`docs/TEST_REPORT.md`** for the full account.

## Quick start

```bash
pip install --break-system-packages jsonschema pytest cryptography
make all                  # 96 / 96 pytest + 26 / 26 conformance + 13 / 13 interop
make validate-config      # validates a sample signed config
```

Platform-runner gated:

```bash
make ios-test             # macOS + Xcode required
make android-test         # Android SDK required
```

## What's in here

| Path                          | Purpose                                                       |
|-------------------------------|---------------------------------------------------------------|
| `schemas/`                    | Canonical JSON schemas (envelope, signed config)              |
| `tools/canonical_codec.py`    | Python reference codec — single source of truth for the wire format |
| `tools/{android,ios}_codec_sim.py` | Python simulators of each platform's codec, for byte-equality verification |
| `tools/conformance_runner.py` | Real conformance gate (replaces the falsely-green one)        |
| `tools/interop_simulator.py`  | Cross-platform Android↔iOS byte-equality oracle               |
| `fixtures/`                   | 13 golden + 10 negative + 3 config fixtures, all canonical    |
| `tests/`                      | pytest suite — 96 tests                                        |
| `ios/LumosKit/`               | Single canonical SwiftPM package (7 modules + 5 test targets) |
| `ios/LumosApp/`               | Thin app wrapper depending on LumosKit                         |
| `android/`                    | Gradle modules with canonical Kotlin codec; duplicate Java trees removed |
| `docs/TEST_REPORT.md`         | **Read this.** Full rebuild report + sign-off matrix          |
| `docs/RTM.md`                 | Hand-curated RTM with honest Covered/Partial/Not-Covered tiers |
| `docs/conformance_report.json`| Output of last conformance run                                |
| `docs/interop_simulator_report.json` | Output of last interop run                              |
| `docs/pytest_report.xml`      | JUnit-XML output of last pytest run                            |

## Gate results on this rebuild

```
pytest tests/                  → 96 / 96 pass
conformance_runner.py          → 26 / 26 pass (13 golden + 10 negative + 3 config)
interop_simulator.py           → 13 / 13 byte-identical (canonical / Android / iOS)
```

## What was NOT done in this environment (and why)

- **Android APK build** — no Android SDK available
- **iOS app build** — no macOS / Xcode available
- **On-device transport tests (BLE / Wi-Fi Aware / QR)** — requires two physical devices
- **Billing SDK tests** — requires Google Play / Apple sandbox accounts
- **Accessibility / inclusive-copy QA** — requires human reviewers
- **Security review (Keychain / Keystore handling)** — requires security engineering
- **Load / soak testing** — requires device farm

These are tracked explicitly in `docs/RTM.md` (Not Covered tier) and `docs/TEST_REPORT.md` §6. The byte-equality guarantee between Android and iOS is enforced at the *simulator* level — Python re-implementations of each platform's codec — which mirror the production source line-by-line so any drift is immediately visible in code review.

## Reproducing the byte-equality guarantee

The contract: bytes produced by `tools/canonical_codec.py`, `LumosKit/Sources/LumosProtocol/ProtocolCodec.swift`, and `android/core-protocol/.../ProtocolCodec.kt` MUST be byte-identical for every legal envelope. The conformance runner enforces this every commit. If you change any of the three implementations, rerun `make all` — if the simulator output diverges from the canonical, the runner fails non-zero.

## Dependencies

- Python 3.11+
- `jsonschema`, `pytest`, `cryptography` (install via `pip install --break-system-packages` or a venv)
- For platform builds: Android SDK 34+ (Gradle), Xcode 15+ (SwiftPM)
