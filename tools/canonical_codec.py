"""Lumos canonical wire-format codec (Python reference implementation).

This is the SINGLE source of truth for the Lumos protocol envelope.
Per Lumos Protocol & Interop Contract v1.0 §3:
- Byte order, max field lengths, enum values, and serialization order SHALL be
  fixed and versioned.

The Android Kotlin codec (core-protocol/.../ProtocolCodec.kt) and the iOS Swift
codec (LumosKit/.../ProtocolCodec.swift) MUST produce byte-identical output to
this reference for every fixture. The conformance runner verifies that.

Field order on the wire (canonical):
  envelope: v, msgType, sessionId, messageId, sentAtMs, transport, payloadEncoding
  payload:  type-specific, in declaration order below

JSON serialization rules:
- UTF-8, no BOM
- Compact (no spaces), separators=(",", ":")
- Keys are emitted in the order declared in this module, NOT sorted
- No trailing newline
- Numbers: integers as integers (no .0), no scientific notation
- Booleans: true/false (lowercase)
- Strings: ASCII-safe; non-ASCII via \\uXXXX escapes (ensure_ascii=True)
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple


# ===== Enums =====

class MsgType(str, Enum):
    HELLO = "HELLO"
    HELLO_ACK = "HELLO_ACK"
    INTEREST_REQUEST = "INTEREST_REQUEST"
    INTEREST_RESPONSE = "INTEREST_RESPONSE"
    MATCH_ESTABLISHED = "MATCH_ESTABLISHED"
    CHAT_TEXT = "CHAT_TEXT"
    CHAT_MEDIA_CHUNK = "CHAT_MEDIA_CHUNK"
    CHAT_MEDIA_ACK = "CHAT_MEDIA_ACK"
    HEARTBEAT = "HEARTBEAT"
    ERROR = "ERROR"
    TRANSPORT_MIGRATE = "TRANSPORT_MIGRATE"
    GOODBYE = "GOODBYE"


class TransportKind(str, Enum):
    BLE = "BLE"
    WIFI = "WIFI"
    QR = "QR"


class PayloadEncoding(str, Enum):
    JSON = "JSON"


# ===== Errors =====

class CodecError(ValueError):
    """Raised on any malformed envelope or payload."""
    pass


# ===== Envelope =====

@dataclass(frozen=True)
class Envelope:
    v: int
    msgType: MsgType
    sessionId: str
    messageId: str
    sentAtMs: int
    transport: TransportKind
    payloadEncoding: PayloadEncoding = PayloadEncoding.JSON


# Canonical envelope key order. Codecs MUST emit in this order.
_ENV_KEYS_ORDER: Tuple[str, ...] = (
    "v", "msgType", "sessionId", "messageId",
    "sentAtMs", "transport", "payloadEncoding",
)

# Canonical payload key order, per msgType.
_PAYLOAD_KEYS_ORDER: Dict[MsgType, Tuple[str, ...]] = {
    MsgType.HELLO:             ("protocolVersions","transports","aeadSuites","kdfSuites","curves","features","nonce"),
    MsgType.HELLO_ACK:         ("selectedProtocolVersion","selectedTransport","selectedAead","selectedKdf","selectedCurve","featuresAccepted"),
    MsgType.INTEREST_REQUEST:  ("previewProfile","interestToken"),
    MsgType.INTEREST_RESPONSE: ("accepted","reason"),
    MsgType.MATCH_ESTABLISHED: ("matchId",),
    MsgType.CHAT_TEXT:         ("ciphertextB64","aadHashHex","ratchetIndex"),
    MsgType.CHAT_MEDIA_CHUNK:  ("transferId","chunkIndex","isLast","ciphertextB64","sha256Hex"),
    MsgType.CHAT_MEDIA_ACK:    ("transferId","highestContiguousChunk"),
    MsgType.HEARTBEAT:         ("seq",),
    MsgType.ERROR:             ("code","retryable","message"),
    MsgType.TRANSPORT_MIGRATE: ("proposed",),
    MsgType.GOODBYE:           ("reason",),
}

# Limits (Interop Contract §3: "max field lengths")
MAX_ENVELOPE_BYTES = 32 * 1024  # 32 KiB hard ceiling for any single envelope
MIN_ID_LEN = 8
MAX_ID_LEN = 64
SESSION_ID_PATTERN = "^[A-Za-z0-9_-]+$"

import re
_ID_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")


def _check_id(label: str, val: str) -> None:
    if not isinstance(val, str) or not _ID_RE.match(val):
        raise CodecError(f"{label} must match {_ID_RE.pattern}, got {val!r}")


def _ordered(keys: Tuple[str, ...], src: Dict[str, Any], allow_missing: Tuple[str, ...] = ()) -> Dict[str, Any]:
    """Return a dict with keys re-emitted in `keys` order. Missing optional keys (in allow_missing) are dropped."""
    out: Dict[str, Any] = {}
    for k in keys:
        if k in src:
            out[k] = src[k]
        elif k not in allow_missing:
            raise CodecError(f"missing required field {k!r}")
    extras = set(src.keys()) - set(keys)
    if extras:
        raise CodecError(f"unknown fields: {sorted(extras)}")
    return out


# ===== Encode =====

def encode(envelope: Envelope, payload: Dict[str, Any]) -> bytes:
    """Encode an envelope+payload to canonical wire bytes (UTF-8 JSON)."""
    if envelope.v != 1:
        raise CodecError(f"protocol v must be 1, got {envelope.v}")
    _check_id("sessionId", envelope.sessionId)
    _check_id("messageId", envelope.messageId)
    if not (0 <= envelope.sentAtMs <= 4_102_444_800_000):
        raise CodecError(f"sentAtMs out of range: {envelope.sentAtMs}")

    env_obj = {
        "v": envelope.v,
        "msgType": envelope.msgType.value,
        "sessionId": envelope.sessionId,
        "messageId": envelope.messageId,
        "sentAtMs": int(envelope.sentAtMs),
        "transport": envelope.transport.value,
        "payloadEncoding": envelope.payloadEncoding.value,
    }
    payload_obj = _normalize_payload(envelope.msgType, payload)

    root = {"envelope": env_obj, "payload": payload_obj}
    out = json.dumps(root, ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    if len(out) > MAX_ENVELOPE_BYTES:
        raise CodecError(f"encoded envelope exceeds {MAX_ENVELOPE_BYTES} bytes: {len(out)}")
    return out


def _normalize_payload(t: MsgType, p: Dict[str, Any]) -> Dict[str, Any]:
    keys = _PAYLOAD_KEYS_ORDER[t]
    if t == MsgType.HELLO:
        return _ordered(keys, p)
    if t == MsgType.HELLO_ACK:
        return _ordered(keys, p)
    if t == MsgType.INTEREST_REQUEST:
        out = _ordered(keys, p)
        # Normalize previewProfile order: alias, tags, intent
        prof = out["previewProfile"]
        if not isinstance(prof, dict):
            raise CodecError("previewProfile must be object")
        out["previewProfile"] = _ordered(("alias","tags","intent"), prof)
        return out
    if t == MsgType.INTEREST_RESPONSE:
        return _ordered(keys, p, allow_missing=("reason",))
    if t == MsgType.MATCH_ESTABLISHED:
        return _ordered(keys, p)
    if t == MsgType.CHAT_TEXT:
        return _ordered(keys, p)
    if t == MsgType.CHAT_MEDIA_CHUNK:
        return _ordered(keys, p)
    if t == MsgType.CHAT_MEDIA_ACK:
        return _ordered(keys, p)
    if t == MsgType.HEARTBEAT:
        return _ordered(keys, p)
    if t == MsgType.ERROR:
        return _ordered(keys, p, allow_missing=("message",))
    if t == MsgType.TRANSPORT_MIGRATE:
        return _ordered(keys, p)
    if t == MsgType.GOODBYE:
        return _ordered(keys, p)
    raise CodecError(f"unhandled msgType {t}")


# ===== Decode =====

def decode(raw: bytes) -> Tuple[Envelope, Dict[str, Any]]:
    """Decode wire bytes into (Envelope, payload). Raises CodecError on any defect."""
    if not isinstance(raw, (bytes, bytearray)):
        raise CodecError("input must be bytes")
    if len(raw) > MAX_ENVELOPE_BYTES:
        raise CodecError(f"input exceeds {MAX_ENVELOPE_BYTES} bytes")
    try:
        root = json.loads(raw.decode("utf-8"))
    except UnicodeDecodeError as e:
        raise CodecError(f"not valid UTF-8: {e}")
    except json.JSONDecodeError as e:
        raise CodecError(f"malformed JSON: {e}")
    if not isinstance(root, dict):
        raise CodecError("root must be JSON object")
    extras = set(root.keys()) - {"envelope", "payload", "_meta"}
    if extras:
        raise CodecError(f"unknown root fields: {sorted(extras)}")
    if "envelope" not in root or "payload" not in root:
        raise CodecError("missing envelope or payload")
    env_d = root["envelope"]
    if not isinstance(env_d, dict):
        raise CodecError("envelope must be object")
    payload_d = root["payload"]
    if not isinstance(payload_d, dict):
        raise CodecError("payload must be object")

    # Validate envelope
    extras = set(env_d.keys()) - set(_ENV_KEYS_ORDER)
    if extras:
        raise CodecError(f"unknown envelope fields: {sorted(extras)}")
    missing = set(_ENV_KEYS_ORDER) - set(env_d.keys())
    if missing:
        raise CodecError(f"missing envelope fields: {sorted(missing)}")

    try:
        msg_type = MsgType(env_d["msgType"])
    except ValueError:
        raise CodecError(f"unknown msgType: {env_d['msgType']!r}")
    try:
        transport = TransportKind(env_d["transport"])
    except ValueError:
        raise CodecError(f"unknown transport: {env_d['transport']!r}")
    try:
        encoding = PayloadEncoding(env_d["payloadEncoding"])
    except ValueError:
        raise CodecError(f"unknown payloadEncoding: {env_d['payloadEncoding']!r}")

    if env_d["v"] != 1:
        raise CodecError(f"unsupported protocol version: {env_d['v']!r}")
    _check_id("sessionId", env_d["sessionId"])
    _check_id("messageId", env_d["messageId"])
    if not isinstance(env_d["sentAtMs"], int) or isinstance(env_d["sentAtMs"], bool):
        raise CodecError("sentAtMs must be integer")
    if not (0 <= env_d["sentAtMs"] <= 4_102_444_800_000):
        raise CodecError(f"sentAtMs out of range: {env_d['sentAtMs']}")

    env = Envelope(
        v=env_d["v"],
        msgType=msg_type,
        sessionId=env_d["sessionId"],
        messageId=env_d["messageId"],
        sentAtMs=env_d["sentAtMs"],
        transport=transport,
        payloadEncoding=encoding,
    )

    # Validate payload by msgType (will raise CodecError if shape is bad)
    payload_norm = _normalize_payload(msg_type, payload_d)
    return env, payload_norm


# ===== Round-trip helper =====

def round_trip(raw: bytes) -> bytes:
    """Decode then re-encode. For canonical fixtures, output MUST byte-equal input."""
    env, payload = decode(raw)
    return encode(env, payload)


# ===== Convenience: encode an entire dict to bytes =====

def encode_dict(d: Dict[str, Any]) -> bytes:
    """Encode a {envelope:..., payload:...} dict (with optional _meta stripped) to canonical wire bytes.

    This is strict: it goes through the full decode path (which rejects unknown
    envelope fields, bad enums, ID pattern violations, etc.) before re-encoding.
    """
    if "envelope" not in d or "payload" not in d:
        raise CodecError("input dict needs envelope and payload")
    # Reject unknown root-level keys so we never silently drop user data.
    extra_root = set(d.keys()) - {"envelope", "payload"}
    if extra_root:
        raise CodecError(f"unknown root fields: {sorted(extra_root)}")
    # Materialize the on-wire bytes via JSON and run through full decode for strict validation.
    raw = json.dumps(d, ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    env, payload = decode(raw)
    return encode(env, payload)
