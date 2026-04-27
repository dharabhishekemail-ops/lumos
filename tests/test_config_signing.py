"""Config schema and signing tests."""
from __future__ import annotations

import base64
import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tools.sign_config import canonical_payload_bytes


CONFIG_DIR = ROOT / "fixtures" / "config"
SCHEMA = ROOT / "schemas" / "signed-config.schema.json"


def _strip_meta(d):
    return {k: v for k, v in d.items() if k != "_meta"}


def test_valid_config_passes_schema():
    import jsonschema
    schema = json.loads(SCHEMA.read_text())
    cfg = json.loads((CONFIG_DIR / "config_01_valid.json").read_text())
    jsonschema.validate(_strip_meta(cfg), schema)  # must not raise


def test_unsigned_config_rejected():
    import jsonschema
    schema = json.loads(SCHEMA.read_text())
    cfg = json.loads((CONFIG_DIR / "negative_config_unsigned.json").read_text())
    with pytest.raises(jsonschema.ValidationError):
        jsonschema.validate(_strip_meta(cfg), schema)


def test_unsafe_rate_rejected():
    import jsonschema
    schema = json.loads(SCHEMA.read_text())
    cfg = json.loads((CONFIG_DIR / "negative_config_unsafe_rate.json").read_text())
    with pytest.raises(jsonschema.ValidationError):
        jsonschema.validate(_strip_meta(cfg), schema)


def test_signing_round_trip_with_cryptography():
    """Sign with Ed25519, then verify the signature with the public key."""
    cryptography = pytest.importorskip("cryptography")
    from cryptography.hazmat.primitives.asymmetric import ed25519
    cfg = json.loads((CONFIG_DIR / "config_01_valid.json").read_text())
    cfg.pop("_meta", None)
    payload_bytes = canonical_payload_bytes(cfg["payload"])
    priv = ed25519.Ed25519PrivateKey.generate()
    pub = priv.public_key()
    sig = priv.sign(payload_bytes)
    # Now verify with the public key.
    pub.verify(sig, payload_bytes)  # raises if invalid
    # And tampering must fail:
    bad_payload = dict(cfg["payload"])
    bad_payload["legalCopyVersion"] = "v1.999"
    bad_bytes = canonical_payload_bytes(bad_payload)
    with pytest.raises(Exception):
        pub.verify(sig, bad_bytes)


def test_canonical_payload_bytes_deterministic():
    """Re-canonicalizing the same payload twice yields identical bytes, with no
    separator whitespace between JSON tokens (whitespace inside string values
    like SKU labels is legitimate)."""
    cfg = json.loads((CONFIG_DIR / "config_01_valid.json").read_text())
    cfg.pop("_meta", None)
    a = canonical_payload_bytes(cfg["payload"])
    b = canonical_payload_bytes(cfg["payload"])
    assert a == b
    text = a.decode("utf-8")
    assert "\n" not in text and "\t" not in text
    # No separator whitespace outside string values.
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
            raise AssertionError(f"separator whitespace after {prev!r}")
        prev = ch
