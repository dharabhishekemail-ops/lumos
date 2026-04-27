"""Canonical codec round-trip tests."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tools.canonical_codec import (
    CodecError, encode, decode, encode_dict,
)


GOLDEN_DIR = ROOT / "fixtures" / "golden"
NEGATIVE_DIR = ROOT / "fixtures" / "negative"


def _strip_meta(d: dict) -> dict:
    return {k: v for k, v in d.items() if k != "_meta"}


def _golden_files():
    return sorted(GOLDEN_DIR.glob("*.json"))


@pytest.mark.parametrize("fixture_path", _golden_files(), ids=lambda p: p.name)
def test_golden_round_trip_byte_equal(fixture_path: Path) -> None:
    """Decoding then re-encoding any golden fixture must yield identical bytes."""
    obj = json.loads(fixture_path.read_text())
    stripped = _strip_meta(obj)
    canon = encode_dict(stripped)
    env, payload = decode(canon)
    again = encode(env, payload)
    assert canon == again, f"{fixture_path.name}: round-trip not byte-equal"


@pytest.mark.parametrize("fixture_path", _golden_files(), ids=lambda p: p.name)
def test_golden_canonical_is_compact(fixture_path: Path) -> None:
    """Canonical bytes must use compact separators: no whitespace between JSON tokens.
    Whitespace inside string values (like 'AEAD verification failed') is legitimate.
    The contract is: no ', ' (comma-space) and no ': ' (colon-space) outside strings,
    no newlines, no tabs, no trailing newline.
    """
    obj = json.loads(fixture_path.read_text())
    canon = encode_dict(_strip_meta(obj))
    text = canon.decode("utf-8")
    # No newlines or tabs anywhere.
    assert "\n" not in text and "\t" not in text
    # No trailing whitespace.
    assert not text.endswith(" ")
    # Walk the bytes outside of strings: no separator whitespace.
    in_string = False
    escaped = False
    prev = ""
    for ch in text:
        if escaped:
            escaped = False
        elif ch == "\\" and in_string:
            escaped = True
        elif ch == '"':
            in_string = not in_string
        elif not in_string and ch == " " and prev in (",", ":"):
            raise AssertionError(f"separator whitespace after {prev!r} in canonical output")
        prev = ch


def test_envelope_size_limit() -> None:
    """Encoded envelopes that exceed 32 KiB must be rejected."""
    from tools.canonical_codec import Envelope, MsgType, TransportKind, encode
    huge_b64 = "A" * (40 * 1024)  # 40 KiB of base64, ~ 30 KiB binary
    env = Envelope(1, MsgType.CHAT_TEXT, "s_session01", "m_msg00007x", 0, TransportKind.WIFI)
    payload = {
        "ciphertextB64": huge_b64,
        "aadHashHex": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "ratchetIndex": 0,
    }
    with pytest.raises(CodecError):
        encode(env, payload)


def test_v_must_be_one() -> None:
    from tools.canonical_codec import Envelope, MsgType, TransportKind, encode
    env = Envelope(2, MsgType.HEARTBEAT, "s_session01", "m_msg00007x", 0, TransportKind.WIFI)
    with pytest.raises(CodecError):
        encode(env, {"seq": 1})


def test_id_pattern_enforced() -> None:
    from tools.canonical_codec import Envelope, MsgType, TransportKind, encode
    env = Envelope(1, MsgType.HEARTBEAT, "$$$$$$$$", "m_msg00007x", 0, TransportKind.WIFI)
    with pytest.raises(CodecError):
        encode(env, {"seq": 1})


def test_negative_fixtures_rejected() -> None:
    """Every negative fixture (except orchestrator-replay) must be rejected by the codec."""
    for f in sorted(NEGATIVE_DIR.iterdir()):
        if f.suffix == ".bin":
            with pytest.raises(CodecError):
                decode(f.read_bytes())
        elif f.suffix == ".json":
            obj = json.loads(f.read_text())
            if obj.get("_meta", {}).get("purpose") == "orchestrator-replay":
                # Codec accepts replay-shaped fixtures; orchestrator handles them.
                encode_dict(_strip_meta(obj))
            else:
                with pytest.raises((CodecError, KeyError, ValueError, TypeError)):
                    encode_dict(_strip_meta(obj))
