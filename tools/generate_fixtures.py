#!/usr/bin/env python3
"""Generate all canonical fixtures from the reference codec.

Per Lumos Interop Test Spec & Fixtures v1.0 §2:
- golden/: canonical serialized examples per message type
- negative/: malformed lengths, enums, truncation, duplicates, replays
- Each fixture has _meta with fixtureId, protocolVersion, expectedResult.

This script is the single source of fixture truth. Re-run after any protocol
contract change. Output is written to fixtures/.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# Allow running from repo root or from tools/.
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tools.canonical_codec import (
    CodecError, Envelope, MsgType, PayloadEncoding, TransportKind, encode,
)


FIXTURES = ROOT / "fixtures"
GOLDEN = FIXTURES / "golden"
NEGATIVE = FIXTURES / "negative"
CONFIG = FIXTURES / "config"


META_OK = {
    "protocolVersion": "1.0",
    "expectedResult": "PASS",
    "purpose": "interop",
    "toolVersion": "lumos-rc1-v1.4",
}
META_REJECT = {
    "protocolVersion": "1.0",
    "expectedResult": "REJECT",
    "purpose": "interop-negative",
    "toolVersion": "lumos-rc1-v1.4",
}


def _write(path: Path, data: dict, fixture_id: str, expected_pass: bool = True) -> None:
    meta = dict(META_OK if expected_pass else META_REJECT, fixtureId=fixture_id)
    out = {**data, "_meta": meta}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")


def _fixture(env: Envelope, payload: dict) -> dict:
    """Encode then decode-back-to-dict to guarantee canonical key order on disk."""
    raw = encode(env, payload)
    obj = json.loads(raw.decode("utf-8"))
    return obj


def gen_golden() -> int:
    n = 0

    # 01 — HELLO
    env = Envelope(1, MsgType.HELLO, "s_session01", "m_msg00001x", 1_730_000_000_000, TransportKind.BLE)
    p = {
        "protocolVersions": [1],
        "transports": ["BLE", "WIFI", "QR"],
        "aeadSuites": ["CHACHA20_POLY1305", "AES_256_GCM"],
        "kdfSuites": ["HKDF_SHA256"],
        "curves": ["X25519"],
        "features": ["CHAT_TEXT", "CHAT_MEDIA_PHOTO", "VOUCHER_QR"],
        "nonce": "Tm9uY2VBQUFBQUFBQQ==",
    }
    _write(GOLDEN / "01_hello.json", _fixture(env, p), "golden_01_hello")
    n += 1

    # 02 — HELLO_ACK
    env = Envelope(1, MsgType.HELLO_ACK, "s_session01", "m_msg00002x", 1_730_000_000_050, TransportKind.BLE)
    p = {
        "selectedProtocolVersion": 1,
        "selectedTransport": "BLE",
        "selectedAead": "CHACHA20_POLY1305",
        "selectedKdf": "HKDF_SHA256",
        "selectedCurve": "X25519",
        "featuresAccepted": ["CHAT_TEXT", "CHAT_MEDIA_PHOTO"],
    }
    _write(GOLDEN / "02_hello_ack.json", _fixture(env, p), "golden_02_hello_ack")
    n += 1

    # 03 — INTEREST_REQUEST
    env = Envelope(1, MsgType.INTEREST_REQUEST, "s_session01", "m_msg00003x", 1_730_000_000_100, TransportKind.WIFI)
    p = {
        "previewProfile": {"alias": "Luna", "tags": ["music", "coffee"], "intent": "chat"},
        "interestToken": "tok_abc123def456",
    }
    _write(GOLDEN / "03_interest_request.json", _fixture(env, p), "golden_03_interest_request")
    n += 1

    # 04 — INTEREST_RESPONSE (accepted)
    env = Envelope(1, MsgType.INTEREST_RESPONSE, "s_session01", "m_msg00004x", 1_730_000_000_150, TransportKind.WIFI)
    p = {"accepted": True}
    _write(GOLDEN / "04a_interest_response_accept.json", _fixture(env, p), "golden_04a_interest_response_accept")
    n += 1

    # 04b — INTEREST_RESPONSE (rejected with reason)
    env = Envelope(1, MsgType.INTEREST_RESPONSE, "s_session01", "m_msg00005x", 1_730_000_000_200, TransportKind.WIFI)
    p = {"accepted": False, "reason": "rate_limited"}
    _write(GOLDEN / "04b_interest_response_reject.json", _fixture(env, p), "golden_04b_interest_response_reject")
    n += 1

    # 05 — MATCH_ESTABLISHED
    env = Envelope(1, MsgType.MATCH_ESTABLISHED, "s_session01", "m_msg00006x", 1_730_000_000_250, TransportKind.WIFI)
    p = {"matchId": "match_zzzzzzz1"}
    _write(GOLDEN / "05_match_established.json", _fixture(env, p), "golden_05_match_established")
    n += 1

    # 06 — CHAT_TEXT
    env = Envelope(1, MsgType.CHAT_TEXT, "s_session01", "m_msg00007x", 1_730_000_000_300, TransportKind.WIFI)
    p = {
        "ciphertextB64": "aGVsbG8gd29ybGQ=",
        "aadHashHex": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "ratchetIndex": 0,
    }
    _write(GOLDEN / "06_chat_text.json", _fixture(env, p), "golden_06_chat_text")
    n += 1

    # 07 — CHAT_MEDIA_CHUNK (first of N)
    env = Envelope(1, MsgType.CHAT_MEDIA_CHUNK, "s_session01", "m_msg00008x", 1_730_000_000_350, TransportKind.WIFI)
    p = {
        "transferId": "xfer_aaaaaaa1",
        "chunkIndex": 0,
        "isLast": False,
        "ciphertextB64": "AAECAwQFBgcICQoLDA0ODw==",
        "sha256Hex": "9d4c2f636f067e8f3eaa3c6e0e98a7bdc62c9e1baeb6d3c1a4fa4f17e35e5e8b",
    }
    _write(GOLDEN / "07_chat_media_chunk.json", _fixture(env, p), "golden_07_chat_media_chunk")
    n += 1

    # 08 — CHAT_MEDIA_ACK
    env = Envelope(1, MsgType.CHAT_MEDIA_ACK, "s_session01", "m_msg00009x", 1_730_000_000_400, TransportKind.WIFI)
    p = {"transferId": "xfer_aaaaaaa1", "highestContiguousChunk": 0}
    _write(GOLDEN / "08_chat_media_ack.json", _fixture(env, p), "golden_08_chat_media_ack")
    n += 1

    # 09 — HEARTBEAT
    env = Envelope(1, MsgType.HEARTBEAT, "s_session01", "m_msg00010x", 1_730_000_000_450, TransportKind.WIFI)
    p = {"seq": 42}
    _write(GOLDEN / "09_heartbeat.json", _fixture(env, p), "golden_09_heartbeat")
    n += 1

    # 10 — TRANSPORT_MIGRATE
    env = Envelope(1, MsgType.TRANSPORT_MIGRATE, "s_session01", "m_msg00011x", 1_730_000_000_500, TransportKind.BLE)
    p = {"proposed": ["WIFI", "BLE"]}
    _write(GOLDEN / "10_transport_migrate.json", _fixture(env, p), "golden_10_transport_migrate")
    n += 1

    # 11 — ERROR
    env = Envelope(1, MsgType.ERROR, "s_session01", "m_msg00012x", 1_730_000_000_550, TransportKind.WIFI)
    p = {"code": "AUTH_TAG_FAIL", "retryable": False, "message": "AEAD verification failed"}
    _write(GOLDEN / "11_error.json", _fixture(env, p), "golden_11_error")
    n += 1

    # 12 — GOODBYE
    env = Envelope(1, MsgType.GOODBYE, "s_session01", "m_msg00013x", 1_730_000_000_600, TransportKind.WIFI)
    p = {"reason": "USER_INITIATED"}
    _write(GOLDEN / "12_goodbye.json", _fixture(env, p), "golden_12_goodbye")
    n += 1

    return n


def gen_negative() -> int:
    """Negative fixtures — every one MUST be rejected by the conformance runner."""
    n = 0

    # neg01 — truncated JSON
    NEGATIVE.mkdir(parents=True, exist_ok=True)
    (NEGATIVE / "neg01_truncated.bin").write_bytes(b'{"envelope":{"v":1,"msgType":"HELL')
    # No _meta because raw bytes; runner detects negative by filename + folder.
    n += 1

    # neg02 — bad msgType enum
    bad = {
        "envelope": {
            "v": 1, "msgType": "NOT_A_REAL_TYPE",
            "sessionId": "s_session01", "messageId": "m_msg00001x",
            "sentAtMs": 1_730_000_000_000, "transport": "BLE", "payloadEncoding": "JSON",
        },
        "payload": {},
        "_meta": {**META_REJECT, "fixtureId": "neg02_bad_msg_type"},
    }
    (NEGATIVE / "neg02_bad_msg_type.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg03 — missing required envelope field
    bad = {
        "envelope": {
            "v": 1, "msgType": "HELLO",
            "messageId": "m_msg00001x", "sentAtMs": 1_730_000_000_000,
            "transport": "BLE", "payloadEncoding": "JSON",
        },
        "payload": {},
        "_meta": {**META_REJECT, "fixtureId": "neg03_missing_session_id"},
    }
    (NEGATIVE / "neg03_missing_session_id.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg04 — bad transport enum
    bad = {
        "envelope": {
            "v": 1, "msgType": "HELLO",
            "sessionId": "s_session01", "messageId": "m_msg00001x",
            "sentAtMs": 1_730_000_000_000, "transport": "WIFI_LOCAL", "payloadEncoding": "JSON",
        },
        "payload": {
            "protocolVersions": [1], "transports": ["BLE"],
            "aeadSuites": ["CHACHA20_POLY1305"], "kdfSuites": ["HKDF_SHA256"],
            "curves": ["X25519"], "features": [], "nonce": "Tm9uY2VBQUFBQUFBQQ==",
        },
        "_meta": {**META_REJECT, "fixtureId": "neg04_bad_transport"},
    }
    (NEGATIVE / "neg04_bad_transport.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg05 — sessionId pattern violation
    bad = {
        "envelope": {
            "v": 1, "msgType": "HEARTBEAT",
            "sessionId": "$$$BAD$$$", "messageId": "m_msg00001x",
            "sentAtMs": 1_730_000_000_000, "transport": "BLE", "payloadEncoding": "JSON",
        },
        "payload": {"seq": 1},
        "_meta": {**META_REJECT, "fixtureId": "neg05_bad_session_id_pattern"},
    }
    (NEGATIVE / "neg05_bad_session_id_pattern.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg06 — wrong protocol major version
    bad = {
        "envelope": {
            "v": 99, "msgType": "HEARTBEAT",
            "sessionId": "s_session01", "messageId": "m_msg00001x",
            "sentAtMs": 1_730_000_000_000, "transport": "BLE", "payloadEncoding": "JSON",
        },
        "payload": {"seq": 1},
        "_meta": {**META_REJECT, "fixtureId": "neg06_unsupported_version"},
    }
    (NEGATIVE / "neg06_unsupported_version.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg07 — extra unknown envelope field (additionalProperties=false)
    bad = {
        "envelope": {
            "v": 1, "msgType": "HEARTBEAT",
            "sessionId": "s_session01", "messageId": "m_msg00001x",
            "sentAtMs": 1_730_000_000_000, "transport": "BLE", "payloadEncoding": "JSON",
            "extraField": "should_be_rejected",
        },
        "payload": {"seq": 1},
        "_meta": {**META_REJECT, "fixtureId": "neg07_unknown_envelope_field"},
    }
    (NEGATIVE / "neg07_unknown_envelope_field.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg08 — payload missing required field for msgType
    bad = {
        "envelope": {
            "v": 1, "msgType": "CHAT_TEXT",
            "sessionId": "s_session01", "messageId": "m_msg00001x",
            "sentAtMs": 1_730_000_000_000, "transport": "WIFI", "payloadEncoding": "JSON",
        },
        "payload": {"ciphertextB64": "aGVsbG8="},  # missing aadHashHex, ratchetIndex
        "_meta": {**META_REJECT, "fixtureId": "neg08_chat_missing_fields"},
    }
    (NEGATIVE / "neg08_chat_missing_fields.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg09 — replay duplicate (same sessionId+messageId pair as a golden)
    # The replay-detection layer is at the orchestrator, not the codec, but we ship a
    # fixture for the orchestrator-level interop test in test_replay_detection.
    bad = {
        "envelope": {
            "v": 1, "msgType": "CHAT_TEXT",
            "sessionId": "s_session01", "messageId": "m_msg00007x",  # same as golden 06
            "sentAtMs": 1_730_000_000_300, "transport": "WIFI", "payloadEncoding": "JSON",
        },
        "payload": {
            "ciphertextB64": "aGVsbG8gd29ybGQ=",
            "aadHashHex": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "ratchetIndex": 0,
        },
        "_meta": {**META_REJECT, "fixtureId": "neg09_replay_duplicate", "purpose": "orchestrator-replay"},
    }
    (NEGATIVE / "neg09_replay_duplicate.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    # neg10 — sentAtMs out of range
    bad = {
        "envelope": {
            "v": 1, "msgType": "HEARTBEAT",
            "sessionId": "s_session01", "messageId": "m_msg00001x",
            "sentAtMs": -1, "transport": "BLE", "payloadEncoding": "JSON",
        },
        "payload": {"seq": 1},
        "_meta": {**META_REJECT, "fixtureId": "neg10_negative_timestamp"},
    }
    (NEGATIVE / "neg10_negative_timestamp.json").write_text(json.dumps(bad, indent=2) + "\n")
    n += 1

    return n


def gen_config() -> int:
    """Signed config fixtures."""
    CONFIG.mkdir(parents=True, exist_ok=True)
    n = 0

    # Valid signed config (signature is a placeholder — real signing happens via tools/sign_config.py)
    valid = {
        "schemaVersion": 1,
        "configVersion": "1.0.0",
        "issuedAt": "2026-03-01T00:00:00Z",
        "expiresAt": "2026-09-01T00:00:00Z",
        "payload": {
            "featureFlags": {
                "adsTabEnabled": True,
                "vouchersEnabled": True,
                "operatorModeEnabled": False,
                "diagnosticsPanelEnabled": True,
            },
            "transport": {"priorityOrder": ["WIFI", "BLE", "QR"], "retryBudget": 5, "backoffMaxMs": 8000},
            "rateLimits": {"interestPerHour": 30, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0",
            "monetization": {
                "freePreviewMinutes": 30,
                "skus": [
                    {"id": "lumos.pro.month", "type": "SUBS", "label": "Lumos Pro (monthly)"},
                    {"id": "lumos.pro.year",  "type": "SUBS", "label": "Lumos Pro (yearly)"},
                ],
            },
            "rolloutPercent": 100,
        },
        "signature": {"alg": "ed25519", "keyId": "lumos-prod-key-v1", "sigB64": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="},
        "_meta": {**META_OK, "fixtureId": "config_01_valid"},
    }
    (CONFIG / "config_01_valid.json").write_text(json.dumps(valid, indent=2) + "\n")
    n += 1

    # Negative — unsigned config
    unsigned = {
        "schemaVersion": 1,
        "configVersion": "1.0.0",
        "issuedAt": "2026-03-01T00:00:00Z",
        "expiresAt": "2026-09-01T00:00:00Z",
        "payload": {
            "featureFlags": {"adsTabEnabled": True},
            "transport": {"priorityOrder": ["WIFI"], "retryBudget": 5, "backoffMaxMs": 8000},
            "rateLimits": {"interestPerHour": 30, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0",
        },
        # signature deliberately absent
        "_meta": {**META_REJECT, "fixtureId": "negative_config_unsigned"},
    }
    (CONFIG / "negative_config_unsigned.json").write_text(json.dumps(unsigned, indent=2) + "\n")
    n += 1

    # Negative — out-of-bounds rate limit (interestPerHour=999 > max 200)
    out_of_bounds = {
        "schemaVersion": 1,
        "configVersion": "1.0.0",
        "issuedAt": "2026-03-01T00:00:00Z",
        "expiresAt": "2026-09-01T00:00:00Z",
        "payload": {
            "featureFlags": {"adsTabEnabled": True},
            "transport": {"priorityOrder": ["WIFI"], "retryBudget": 5, "backoffMaxMs": 8000},
            "rateLimits": {"interestPerHour": 999, "messagesPerMinute": 60},
            "legalCopyVersion": "v1.0",
        },
        "signature": {"alg": "ed25519", "keyId": "k", "sigB64": "AAAA"},
        "_meta": {**META_REJECT, "fixtureId": "negative_config_unsafe_rate"},
    }
    (CONFIG / "negative_config_unsafe_rate.json").write_text(json.dumps(out_of_bounds, indent=2) + "\n")
    n += 1

    return n


def main() -> int:
    g = gen_golden()
    n = gen_negative()
    c = gen_config()
    print(f"generated {g} golden + {n} negative + {c} config fixtures")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
