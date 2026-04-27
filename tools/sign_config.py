#!/usr/bin/env python3
"""Sign a Lumos config pack with Ed25519.

Signing input contract (Lumos Signed Config Schema & Governance Spec v1.0 §4):
  the bytes that are signed are the `payload` object re-serialized with sorted
  keys, no whitespace, and ensure_ascii=True.

This MUST match the canonicalization done by iOS `ConfigRuntime.canonicalPayloadBytes`
and Android `ConfigSignatureVerifier` exactly, otherwise signature verification
will fail across platforms.

Usage:
  tools/sign_config.py --in fixtures/config/config_01_valid.json \
                       --out fixtures/config/config_01_signed.json
  tools/sign_config.py --keygen --out-priv private.bin --out-pub public.bin
"""
from __future__ import annotations

import argparse
import base64
import json
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric import ed25519
    from cryptography.hazmat.primitives import serialization
    HAS_CRYPTO = True
except Exception:
    HAS_CRYPTO = False


def canonical_payload_bytes(payload: dict) -> bytes:
    """Re-serialize payload with sorted keys, compact, ensure_ascii=True."""
    return json.dumps(payload, sort_keys=True, ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def keygen(priv_path: Path, pub_path: Path) -> None:
    if not HAS_CRYPTO:
        print("error: pip install cryptography", file=sys.stderr)
        sys.exit(2)
    priv = ed25519.Ed25519PrivateKey.generate()
    pub = priv.public_key()
    priv_bytes = priv.private_bytes(serialization.Encoding.Raw,
                                    serialization.PrivateFormat.Raw,
                                    serialization.NoEncryption())
    pub_bytes = pub.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    priv_path.write_bytes(priv_bytes)
    pub_path.write_bytes(pub_bytes)
    print(f"wrote {priv_path} ({len(priv_bytes)}B), {pub_path} ({len(pub_bytes)}B)")


def sign_pack(in_path: Path, out_path: Path, priv_path: Path, key_id: str) -> None:
    if not HAS_CRYPTO:
        print("error: pip install cryptography", file=sys.stderr)
        sys.exit(2)
    pack = json.loads(in_path.read_text())
    pack.pop("_meta", None)
    payload_bytes = canonical_payload_bytes(pack["payload"])
    priv = ed25519.Ed25519PrivateKey.from_private_bytes(priv_path.read_bytes())
    sig = priv.sign(payload_bytes)
    pack["signature"] = {
        "alg": "ed25519",
        "keyId": key_id,
        "sigB64": base64.b64encode(sig).decode("ascii"),
    }
    out_path.write_text(json.dumps(pack, indent=2) + "\n")
    print(f"signed -> {out_path} (sig {len(sig)}B)")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--keygen", action="store_true")
    ap.add_argument("--out-priv", type=Path, default=Path("config_priv.bin"))
    ap.add_argument("--out-pub",  type=Path, default=Path("config_pub.bin"))
    ap.add_argument("--in",       dest="in_path", type=Path)
    ap.add_argument("--out",      dest="out_path", type=Path)
    ap.add_argument("--priv",     type=Path, default=Path("config_priv.bin"))
    ap.add_argument("--key-id",   default="lumos-prod-key-v1")
    args = ap.parse_args()
    if args.keygen:
        keygen(args.out_priv, args.out_pub)
        return 0
    if args.in_path and args.out_path:
        sign_pack(args.in_path, args.out_path, args.priv, args.key_id)
        return 0
    ap.print_help()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
