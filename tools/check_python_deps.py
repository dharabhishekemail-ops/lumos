#!/usr/bin/env python3
"""Check required Python packages for Lumos local gates.
Exits non-zero with install guidance when any required dependency is missing.
"""
from __future__ import annotations
import importlib.util
import sys

REQUIRED = {
    "pytest": "pytest",
    "hypothesis": "hypothesis",
    "jsonschema": "jsonschema",
    "cryptography": "cryptography",
}
missing = []
for pkg, mod in REQUIRED.items():
    if importlib.util.find_spec(mod) is None:
        missing.append(pkg)

if missing:
    print("Missing Python test dependencies: " + ", ".join(missing), file=sys.stderr)
    print("Install with: python3 -m pip install -r tools/requirements.txt", file=sys.stderr)
    sys.exit(2)

print("Python dependency check passed: " + ", ".join(REQUIRED.keys()))
