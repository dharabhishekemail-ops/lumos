"""Cross-platform interop tests — Android sim, iOS sim, canonical Python all
produce byte-identical output for every golden fixture.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tools.canonical_codec import encode_dict as canon_encode_dict, decode as canon_decode
from tools import android_codec_sim, ios_codec_sim


GOLDEN_DIR = ROOT / "fixtures" / "golden"


def _strip_meta(d: dict) -> dict:
    return {k: v for k, v in d.items() if k != "_meta"}


def _golden_files():
    return sorted(GOLDEN_DIR.glob("*.json"))


@pytest.mark.parametrize("fixture_path", _golden_files(), ids=lambda p: p.name)
def test_canon_equals_android(fixture_path: Path) -> None:
    obj = json.loads(fixture_path.read_text())
    stripped = _strip_meta(obj)
    canon = canon_encode_dict(stripped)
    android = android_codec_sim.encode_dict(stripped)
    assert canon == android, (
        f"{fixture_path.name}: canon != android\n"
        f"canon:   {canon[:160]!r}\n"
        f"android: {android[:160]!r}"
    )


@pytest.mark.parametrize("fixture_path", _golden_files(), ids=lambda p: p.name)
def test_canon_equals_ios(fixture_path: Path) -> None:
    obj = json.loads(fixture_path.read_text())
    stripped = _strip_meta(obj)
    canon = canon_encode_dict(stripped)
    ios = ios_codec_sim.encode_dict(stripped)
    assert canon == ios


@pytest.mark.parametrize("fixture_path", _golden_files(), ids=lambda p: p.name)
def test_android_equals_ios(fixture_path: Path) -> None:
    obj = json.loads(fixture_path.read_text())
    stripped = _strip_meta(obj)
    a = android_codec_sim.encode_dict(stripped)
    i = ios_codec_sim.encode_dict(stripped)
    assert a == i


@pytest.mark.parametrize("fixture_path", _golden_files(), ids=lambda p: p.name)
def test_cross_decode(fixture_path: Path) -> None:
    """Bytes encoded by one platform must decode cleanly under the other's contract."""
    obj = json.loads(fixture_path.read_text())
    stripped = _strip_meta(obj)
    a = android_codec_sim.encode_dict(stripped)
    i = ios_codec_sim.encode_dict(stripped)
    env_a, p_a = canon_decode(a)
    env_i, p_i = canon_decode(i)
    assert env_a == env_i
    assert p_a == p_i
