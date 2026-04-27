"""Android Kotlin codec simulator (Python).

Mirrors the encoding semantics of `android/core-protocol/src/main/kotlin/com/lumos/protocol/ProtocolCodec.kt`.
Output bytes MUST be identical to the canonical Python codec (and to iOS) for
every fixture, otherwise interop is broken.

This file follows the Kotlin source line-by-line so that any drift between
Kotlin and Python is easy to spot during code review.
"""
from __future__ import annotations

import re
from typing import Any, Dict, List


_ID_RE = re.compile(r"^[A-Za-z0-9_\-]{8,64}$")
MAX_ENVELOPE_BYTES = 32 * 1024


def _validate_id(s: str, field: str) -> None:
    if not isinstance(s, str) or not _ID_RE.match(s):
        raise ValueError(f"{field} must match {_ID_RE.pattern}, got {s!r}")


def _json_string(s: str) -> str:
    """Mirrors Kotlin ProtocolCodec.jsonString: ASCII-safe escaping matching Python ensure_ascii=True."""
    out = ['"']
    i = 0
    while i < len(s):
        ch = s[i]
        code = ord(ch)
        if code == 0x22: out.append('\\"')
        elif code == 0x5C: out.append('\\\\')
        elif code == 0x08: out.append('\\b')
        elif code == 0x0C: out.append('\\f')
        elif code == 0x0A: out.append('\\n')
        elif code == 0x0D: out.append('\\r')
        elif code == 0x09: out.append('\\t')
        elif code < 0x20: out.append(f'\\u{code:04x}')
        elif code < 0x7F: out.append(ch)
        elif code <= 0xFFFF: out.append(f'\\u{code:04x}')
        else:
            # Surrogate pair
            v = code - 0x10000
            hi = 0xD800 + (v >> 10)
            lo = 0xDC00 + (v & 0x3FF)
            out.append(f'\\u{hi:04x}\\u{lo:04x}')
        i += 1
    out.append('"')
    return ''.join(out)


def _str_arr(a: List[str]) -> str:
    return '[' + ','.join(_json_string(s) for s in a) + ']'


def _int_arr(a: List[int]) -> str:
    return '[' + ','.join(str(n) for n in a) + ']'


def _enc_envelope(env: Dict[str, Any]) -> str:
    # Order: v, msgType, sessionId, messageId, sentAtMs, transport, payloadEncoding
    parts: List[str] = []
    parts.append(f'"v":{env["v"]}')
    parts.append(f'"msgType":{_json_string(env["msgType"])}')
    parts.append(f'"sessionId":{_json_string(env["sessionId"])}')
    parts.append(f'"messageId":{_json_string(env["messageId"])}')
    parts.append(f'"sentAtMs":{env["sentAtMs"]}')
    parts.append(f'"transport":{_json_string(env["transport"])}')
    parts.append(f'"payloadEncoding":{_json_string(env["payloadEncoding"])}')
    return '{' + ','.join(parts) + '}'


def _enc_payload(msg_type: str, p: Dict[str, Any]) -> str:
    if msg_type == "HELLO":
        parts = []
        parts.append(f'"protocolVersions":{_int_arr(p["protocolVersions"])}')
        parts.append(f'"transports":{_str_arr(p["transports"])}')
        parts.append(f'"aeadSuites":{_str_arr(p["aeadSuites"])}')
        parts.append(f'"kdfSuites":{_str_arr(p["kdfSuites"])}')
        parts.append(f'"curves":{_str_arr(p["curves"])}')
        parts.append(f'"features":{_str_arr(p["features"])}')
        parts.append(f'"nonce":{_json_string(p["nonce"])}')
        return '{' + ','.join(parts) + '}'
    if msg_type == "HELLO_ACK":
        parts = []
        parts.append(f'"selectedProtocolVersion":{p["selectedProtocolVersion"]}')
        parts.append(f'"selectedTransport":{_json_string(p["selectedTransport"])}')
        parts.append(f'"selectedAead":{_json_string(p["selectedAead"])}')
        parts.append(f'"selectedKdf":{_json_string(p["selectedKdf"])}')
        parts.append(f'"selectedCurve":{_json_string(p["selectedCurve"])}')
        parts.append(f'"featuresAccepted":{_str_arr(p["featuresAccepted"])}')
        return '{' + ','.join(parts) + '}'
    if msg_type == "INTEREST_REQUEST":
        prof = p["previewProfile"]
        prof_s = (
            '{'
            + f'"alias":{_json_string(prof["alias"])}'
            + f',"tags":{_str_arr(prof["tags"])}'
            + f',"intent":{_json_string(prof["intent"])}'
            + '}'
        )
        return '{' + f'"previewProfile":{prof_s}' + f',"interestToken":{_json_string(p["interestToken"])}' + '}'
    if msg_type == "INTEREST_RESPONSE":
        if "reason" in p and p["reason"] is not None:
            return '{' + f'"accepted":{"true" if p["accepted"] else "false"}' + f',"reason":{_json_string(p["reason"])}' + '}'
        return '{' + f'"accepted":{"true" if p["accepted"] else "false"}' + '}'
    if msg_type == "MATCH_ESTABLISHED":
        return '{' + f'"matchId":{_json_string(p["matchId"])}' + '}'
    if msg_type == "CHAT_TEXT":
        return '{' + f'"ciphertextB64":{_json_string(p["ciphertextB64"])}' + f',"aadHashHex":{_json_string(p["aadHashHex"])}' + f',"ratchetIndex":{p["ratchetIndex"]}' + '}'
    if msg_type == "CHAT_MEDIA_CHUNK":
        return ('{'
                + f'"transferId":{_json_string(p["transferId"])}'
                + f',"chunkIndex":{p["chunkIndex"]}'
                + f',"isLast":{"true" if p["isLast"] else "false"}'
                + f',"ciphertextB64":{_json_string(p["ciphertextB64"])}'
                + f',"sha256Hex":{_json_string(p["sha256Hex"])}'
                + '}')
    if msg_type == "CHAT_MEDIA_ACK":
        return '{' + f'"transferId":{_json_string(p["transferId"])}' + f',"highestContiguousChunk":{p["highestContiguousChunk"]}' + '}'
    if msg_type == "HEARTBEAT":
        return '{' + f'"seq":{p["seq"]}' + '}'
    if msg_type == "ERROR":
        if "message" in p and p["message"] is not None:
            return '{' + f'"code":{_json_string(p["code"])}' + f',"retryable":{"true" if p["retryable"] else "false"}' + f',"message":{_json_string(p["message"])}' + '}'
        return '{' + f'"code":{_json_string(p["code"])}' + f',"retryable":{"true" if p["retryable"] else "false"}' + '}'
    if msg_type == "TRANSPORT_MIGRATE":
        return '{' + f'"proposed":{_str_arr(p["proposed"])}' + '}'
    if msg_type == "GOODBYE":
        return '{' + f'"reason":{_json_string(p["reason"])}' + '}'
    raise ValueError(f"unknown msgType: {msg_type}")


def encode(envelope_dict: Dict[str, Any], payload_dict: Dict[str, Any]) -> bytes:
    """Encode a (envelope, payload) pair like the Android Kotlin codec would."""
    if envelope_dict.get("v") != 1:
        raise ValueError("v must be 1")
    _validate_id(envelope_dict["sessionId"], "sessionId")
    _validate_id(envelope_dict["messageId"], "messageId")
    s = '{"envelope":' + _enc_envelope(envelope_dict) + ',"payload":' + _enc_payload(envelope_dict["msgType"], payload_dict) + '}'
    out = s.encode("utf-8")
    if len(out) > MAX_ENVELOPE_BYTES:
        raise ValueError(f"envelope > {MAX_ENVELOPE_BYTES}")
    return out


def encode_dict(d: Dict[str, Any]) -> bytes:
    """Encode a {envelope:..., payload:...} dict (with optional _meta stripped)."""
    return encode(d["envelope"], d["payload"])
