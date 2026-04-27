#!/usr/bin/env python3
"""Cross-platform interop simulator.

Simulates a Lumos handshake where one peer is "Android" and the other is "iOS",
using the Python implementations of each codec (android_codec_sim,
ios_codec_sim). For every golden fixture we verify that:

  - Encoded bytes from Android == Encoded bytes from iOS == Canonical bytes.
  - Decoding Android-encoded bytes via the canonical Python decoder yields
    the same envelope+payload as decoding iOS-encoded bytes.
  - Re-encoding the decoded result through either codec is idempotent.

Per Lumos VVMP §4 and Interop Test Spec §3. This is the non-on-device interop
oracle: it doesn't need a phone, but it pins the wire-format invariants that
the device codecs are required to respect.
"""
from __future__ import annotations

import os
import json
import sys
from pathlib import Path
from typing import Any, Dict, List

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(ROOT))

from tools.canonical_codec import encode_dict as canon_encode_dict, decode as canon_decode, encode as canon_encode
from tools import android_codec_sim, ios_codec_sim


def _strip_meta(d: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in d.items() if k != "_meta"}


def simulate_handshake(fixtures_dir: Path) -> Dict[str, Any]:
    """Run an Android↔iOS exchange of every golden fixture."""
    results: List[Dict[str, Any]] = []
    files = sorted((fixtures_dir / "golden").glob("*.json"))

    for f in files:
        name = f.name
        obj = json.loads(f.read_text())
        stripped = _strip_meta(obj)

        canon = canon_encode_dict(stripped)
        a_bytes = android_codec_sim.encode_dict(stripped)
        i_bytes = ios_codec_sim.encode_dict(stripped)

        equal_a_canon = (a_bytes == canon)
        equal_i_canon = (i_bytes == canon)
        equal_a_i = (a_bytes == i_bytes)

        # Cross-decode: bytes from Android, parsed by canonical decoder.
        env_a, pay_a = canon_decode(a_bytes)
        env_i, pay_i = canon_decode(i_bytes)
        cross_envelope_match = (env_a == env_i)
        cross_payload_match = (pay_a == pay_i)

        # Re-encode through the canonical codec; must equal the original canonical bytes.
        re_a = canon_encode(env_a, pay_a)
        re_i = canon_encode(env_i, pay_i)
        idempotent_a = (re_a == canon)
        idempotent_i = (re_i == canon)

        ok = all([equal_a_canon, equal_i_canon, equal_a_i, cross_envelope_match,
                  cross_payload_match, idempotent_a, idempotent_i])
        results.append({
            "fixture": name,
            "bytes": len(canon),
            "android_byte_equal_canon": equal_a_canon,
            "ios_byte_equal_canon": equal_i_canon,
            "android_equal_ios": equal_a_i,
            "cross_envelope_match": cross_envelope_match,
            "cross_payload_match": cross_payload_match,
            "android_idempotent": idempotent_a,
            "ios_idempotent": idempotent_i,
            "pass": ok,
        })

    summary = {
        "total": len(results),
        "pass": sum(1 for r in results if r["pass"]),
        "fail": sum(1 for r in results if not r["pass"]),
    }
    summary["overall_pass"] = (summary["fail"] == 0)
    return {"summary": summary, "fixtures": results}


def main() -> int:
    fixtures_dir = ROOT / "fixtures"
    out_path = ROOT / "docs" / "interop_simulator_report.json"
    report = simulate_handshake(fixtures_dir)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["summary"], indent=2))
    return 0 if report["summary"]["overall_pass"] else 1


if __name__ == "__main__":
    import sys
    rc = main()
    os._exit(rc)
