"""Property-based tests using Hypothesis.

Hand-written fixtures cover the cases an engineer thought to write. Hypothesis
generates thousands of arbitrary-but-still-valid envelopes from the spec
constraints and asserts the same invariants on each one. Fuzz coverage,
basically.

Per Lumos VVMP §4.2 (codec invariants must hold for all valid inputs, not
just curated samples).
"""
from __future__ import annotations

import json
import string
import sys
from pathlib import Path

import pytest
from hypothesis import given, settings, strategies as st, HealthCheck

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tools.canonical_codec import (
    CodecError, Envelope, MsgType, TransportKind, encode, encode_dict, decode,
)
from tools import android_codec_sim, ios_codec_sim


# ---------------------------------------------------------------------------
# Strategies
# ---------------------------------------------------------------------------

# IDs satisfy ^[A-Za-z0-9_-]{8,64}$
_id_chars = string.ascii_letters + string.digits + "_-"
ids_strategy = st.text(alphabet=_id_chars, min_size=8, max_size=64)

# sentAtMs in [0, 4_102_444_800_000]
sent_at = st.integers(min_value=0, max_value=4_102_444_800_000)

# Envelope-level metadata
envelope_meta = st.fixed_dictionaries({
    "v": st.just(1),
    "sessionId": ids_strategy,
    "messageId": ids_strategy,
    "sentAtMs": sent_at,
    "transport": st.sampled_from(["BLE", "WIFI", "QR"]),
    "payloadEncoding": st.just("JSON"),
})


# ---- Per-msgType payload strategies ----

def _hello_payload():
    return st.fixed_dictionaries({
        "protocolVersions": st.lists(st.integers(min_value=1, max_value=8), min_size=1, max_size=8, unique=True),
        "transports":       st.lists(st.sampled_from(["BLE","WIFI","QR"]), min_size=1, max_size=3, unique=True),
        "aeadSuites":       st.lists(st.sampled_from(["CHACHA20_POLY1305","AES_256_GCM"]), min_size=1, max_size=2, unique=True),
        "kdfSuites":        st.lists(st.just("HKDF_SHA256"), min_size=1, max_size=1),
        "curves":           st.lists(st.just("X25519"), min_size=1, max_size=1),
        "features":         st.lists(st.sampled_from(["CHAT_TEXT","CHAT_MEDIA_PHOTO","VOUCHER_QR"]), min_size=0, max_size=3, unique=True),
        "nonce":            st.text(alphabet=string.ascii_letters + string.digits + "+/=_-", min_size=16, max_size=64),
    })


def _hello_ack_payload():
    return st.fixed_dictionaries({
        "selectedProtocolVersion": st.integers(min_value=1, max_value=1),
        "selectedTransport":       st.sampled_from(["BLE","WIFI","QR"]),
        "selectedAead":            st.sampled_from(["CHACHA20_POLY1305","AES_256_GCM"]),
        "selectedKdf":             st.just("HKDF_SHA256"),
        "selectedCurve":           st.just("X25519"),
        "featuresAccepted":        st.lists(st.sampled_from(["CHAT_TEXT","CHAT_MEDIA_PHOTO","VOUCHER_QR"]), min_size=0, max_size=3, unique=True),
    })


def _interest_request_payload():
    alias_chars = st.text(alphabet=string.ascii_letters + string.digits + " _-.'", min_size=1, max_size=32)
    return st.fixed_dictionaries({
        "previewProfile": st.fixed_dictionaries({
            "alias":  alias_chars,
            "tags":   st.lists(st.text(alphabet=string.ascii_letters, min_size=1, max_size=12), min_size=0, max_size=5),
            "intent": st.sampled_from(["dating","chat","networking"]),
        }),
        "interestToken": st.text(alphabet=string.ascii_letters + string.digits + "_-", min_size=8, max_size=64),
    })


def _interest_response_payload():
    return st.one_of(
        st.fixed_dictionaries({"accepted": st.just(True)}),
        st.fixed_dictionaries({"accepted": st.just(False), "reason": st.text(alphabet=string.printable, min_size=1, max_size=64).filter(lambda s: '\x00' not in s and '\\' not in s)}),
    )


def _match_established_payload():
    return st.fixed_dictionaries({"matchId": ids_strategy})


def _chat_text_payload():
    sha_hex = st.text(alphabet="0123456789abcdef", min_size=64, max_size=64)
    return st.fixed_dictionaries({
        "ciphertextB64": st.text(alphabet=string.ascii_letters + string.digits + "+/=", min_size=4, max_size=512),
        "aadHashHex":    sha_hex,
        "ratchetIndex":  st.integers(min_value=0, max_value=2**63 - 1),
    })


def _chat_media_chunk_payload():
    sha_hex = st.text(alphabet="0123456789abcdef", min_size=64, max_size=64)
    return st.fixed_dictionaries({
        "transferId":    ids_strategy,
        "chunkIndex":    st.integers(min_value=0, max_value=65535),
        "isLast":        st.booleans(),
        "ciphertextB64": st.text(alphabet=string.ascii_letters + string.digits + "+/=", min_size=4, max_size=2048),
        "sha256Hex":     sha_hex,
    })


def _chat_media_ack_payload():
    return st.fixed_dictionaries({
        "transferId":             ids_strategy,
        "highestContiguousChunk": st.integers(min_value=-1, max_value=65535),
    })


def _heartbeat_payload():
    return st.fixed_dictionaries({"seq": st.integers(min_value=0, max_value=2**63 - 1)})


def _error_payload():
    return st.one_of(
        st.fixed_dictionaries({
            "code":      st.sampled_from(["PARSE_ERROR","SCHEMA_ERROR","AUTH_TAG_FAIL","SESSION_UNKNOWN","REPLAY_DETECTED","RATE_LIMITED","INCOMPATIBLE_VERSION","TRANSPORT_FAILED","INTERNAL"]),
            "retryable": st.booleans(),
        }),
        st.fixed_dictionaries({
            "code":      st.sampled_from(["PARSE_ERROR","SCHEMA_ERROR","AUTH_TAG_FAIL","SESSION_UNKNOWN","REPLAY_DETECTED","RATE_LIMITED","INCOMPATIBLE_VERSION","TRANSPORT_FAILED","INTERNAL"]),
            "retryable": st.booleans(),
            "message":   st.text(alphabet=string.printable, min_size=0, max_size=128).filter(lambda s: '\x00' not in s),
        }),
    )


def _transport_migrate_payload():
    return st.fixed_dictionaries({
        "proposed": st.lists(st.sampled_from(["BLE","WIFI","QR"]), min_size=1, max_size=3, unique=True),
    })


def _goodbye_payload():
    return st.fixed_dictionaries({
        "reason": st.sampled_from(["USER_INITIATED","SESSION_TIMEOUT","FATAL_ERROR","PEER_BLOCKED"]),
    })


# Combined: msgType -> matching payload
def _full_envelope():
    cases = [
        ("HELLO",              _hello_payload()),
        ("HELLO_ACK",          _hello_ack_payload()),
        ("INTEREST_REQUEST",   _interest_request_payload()),
        ("INTEREST_RESPONSE",  _interest_response_payload()),
        ("MATCH_ESTABLISHED",  _match_established_payload()),
        ("CHAT_TEXT",          _chat_text_payload()),
        ("CHAT_MEDIA_CHUNK",   _chat_media_chunk_payload()),
        ("CHAT_MEDIA_ACK",     _chat_media_ack_payload()),
        ("HEARTBEAT",          _heartbeat_payload()),
        ("ERROR",              _error_payload()),
        ("TRANSPORT_MIGRATE",  _transport_migrate_payload()),
        ("GOODBYE",            _goodbye_payload()),
    ]
    branches = []
    for mt, payload_st in cases:
        branches.append(st.tuples(st.just(mt), payload_st))
    return st.one_of(*branches)


# ---------------------------------------------------------------------------
# Properties
# ---------------------------------------------------------------------------

@given(envelope_meta, _full_envelope())
@settings(max_examples=400, suppress_health_check=[HealthCheck.too_slow], deadline=None)
def test_canonical_round_trip_property(env_meta, mt_payload):
    """For every valid envelope, decode∘encode is identity."""
    msg_type, payload = mt_payload
    env_dict = dict(env_meta, msgType=msg_type)
    full = {"envelope": env_dict, "payload": payload}
    raw = encode_dict(full)
    decoded_env, decoded_payload = decode(raw)
    again = encode(decoded_env, decoded_payload)
    assert raw == again


@given(envelope_meta, _full_envelope())
@settings(max_examples=400, suppress_health_check=[HealthCheck.too_slow], deadline=None)
def test_three_codecs_byte_equal_property(env_meta, mt_payload):
    """Canonical, Android sim, and iOS sim produce identical bytes for every valid envelope."""
    msg_type, payload = mt_payload
    env_dict = dict(env_meta, msgType=msg_type)
    full = {"envelope": env_dict, "payload": payload}
    canon = encode_dict(full)
    android_b = android_codec_sim.encode_dict(full)
    ios_b = ios_codec_sim.encode_dict(full)
    assert canon == android_b == ios_b


@given(envelope_meta, _full_envelope())
@settings(max_examples=200, suppress_health_check=[HealthCheck.too_slow], deadline=None)
def test_envelope_within_size_limit_property(env_meta, mt_payload):
    """Every envelope our strategies produce stays under the 32 KiB limit."""
    msg_type, payload = mt_payload
    env_dict = dict(env_meta, msgType=msg_type)
    full = {"envelope": env_dict, "payload": payload}
    raw = encode_dict(full)
    assert len(raw) <= 32 * 1024


@given(envelope_meta, _full_envelope())
@settings(max_examples=200, suppress_health_check=[HealthCheck.too_slow], deadline=None)
def test_decode_recovers_original_property(env_meta, mt_payload):
    """Decoded envelope.sessionId/messageId/msgType match the originals."""
    msg_type, payload = mt_payload
    env_dict = dict(env_meta, msgType=msg_type)
    full = {"envelope": env_dict, "payload": payload}
    raw = encode_dict(full)
    decoded_env, _ = decode(raw)
    assert decoded_env.sessionId == env_dict["sessionId"]
    assert decoded_env.messageId == env_dict["messageId"]
    assert decoded_env.msgType.value == msg_type
    assert decoded_env.transport.value == env_dict["transport"]
    assert decoded_env.sentAtMs == env_dict["sentAtMs"]
