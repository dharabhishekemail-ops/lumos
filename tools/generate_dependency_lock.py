#!/usr/bin/env python3
"""Generate dependency lock/evidence for the Python local gates.
This does not install anything; it records required specs plus currently installed versions.
"""
from __future__ import annotations
import hashlib
import importlib.metadata as md
import os
import json
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
REQ = ROOT / "tools" / "requirements.txt"
REQ_TO_DIST = {
    "pytest": "pytest",
    "hypothesis": "hypothesis",
    "jsonschema": "jsonschema",
    "cryptography": "cryptography",
}

def sha256(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()

required_lines = []
for line in REQ.read_text(encoding="utf-8").splitlines():
    s = line.strip()
    if s and not s.startswith("#"):
        required_lines.append(s)

installed = {}
for dist in REQ_TO_DIST.values():
    try:
        installed[dist] = md.version(dist)
    except md.PackageNotFoundError:
        installed[dist] = None

missing = sorted([k for k, v in installed.items() if v is None])
lock = {
    "generated_utc": datetime.now(timezone.utc).isoformat(),
    "requirements_file": "tools/requirements.txt",
    "requirements_sha256": sha256(REQ),
    "required_specs": required_lines,
    "installed_versions": installed,
    "missing": missing,
    "install_command": "python3 -m pip install -r tools/requirements.txt",
}
out_json = ROOT / "docs" / "python_dependency_lock.json"
out_txt = ROOT / "docs" / "python_dependency_lock.txt"
out_json.write_text(json.dumps(lock, indent=2), encoding="utf-8")
lines = ["# Python Dependency Lock / Evidence", "", f"Generated UTC: {lock['generated_utc']}", "", "## Required specs"]
lines += [f"- {x}" for x in required_lines]
lines += ["", "## Installed versions"]
for k, v in installed.items():
    lines.append(f"- {k}: {v if v is not None else 'MISSING'}")
lines += ["", f"requirements.txt SHA-256: `{lock['requirements_sha256']}`", ""]
if missing:
    lines += ["## Missing dependencies", "", ", ".join(missing), "", f"Install with: `{lock['install_command']}`", ""]
out_txt.write_text("\n".join(lines), encoding="utf-8")
print(f"Wrote {out_json}")
print(f"Wrote {out_txt}")
if missing:
    print("Missing dependencies: " + ", ".join(missing))
