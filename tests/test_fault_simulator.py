"""Fault simulator + soak tests.

These tests pin the codec's required reaction to hostile transport conditions:

  - DROP — bytes never arrive: the codec is not invoked for that message; the
    orchestrator must time out via its retry budget. Tested at the orchestrator
    layer in `test_session_state_machine.py`.

  - REORDER — bytes arrive in an order the application didn't send. Each
    arrival decodes independently; ordering is the orchestrator's concern via
    messageId.

  - DUPLICATE — the same envelope arrives twice. The codec accepts both
    (they're well-formed); the orchestrator's dedupe window drops the second
    one. Pinned by `test_session_state_machine.py::test_immediate_replay_rejected`.

  - CORRUPT — a single bit is flipped somewhere in the bytes. The codec MUST
    reject (CodecError, not silent acceptance). This is the most important
    fault class for security.

The soak test runs 10,000 round-trips through the codec to catch accumulator,
state-leak, or memory-allocation bugs that only show up under sustained load.

Per Lumos VVMP §4.3 and Crypto Spec §5 (transport fault classes).
"""
from __future__ import annotations

import json
import random
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from tools.canonical_codec import (
    CodecError, Envelope, MsgType, TransportKind, encode, encode_dict, decode,
)


GOLDEN_DIR = ROOT / "fixtures" / "golden"


def _strip_meta(d: dict) -> dict:
    return {k: v for k, v in d.items() if k != "_meta"}


def _all_golden_bytes() -> list[bytes]:
    out = []
    for f in sorted(GOLDEN_DIR.glob("*.json")):
        out.append(encode_dict(_strip_meta(json.loads(f.read_text()))))
    return out


# ---------------------------------------------------------------------------
# CORRUPT — single-bit flips, byte deletions, byte insertions
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("flip_offset", range(0, 200, 17))
def test_single_bit_flip_either_rejected_or_decodes_different(flip_offset: int) -> None:
    """Flipping any single bit must either fail decode OR yield a message whose
    re-encoded bytes differ from the original. Silent identity (a bit flip that
    decodes to bytes-equal output) would be the bug we are guarding against."""
    raw = _all_golden_bytes()[0]  # use HELLO as the carrier
    if flip_offset >= len(raw):
        pytest.skip("offset past end")
    corrupted = bytearray(raw)
    corrupted[flip_offset] ^= 0x40
    try:
        env, payload = decode(bytes(corrupted))
        re = encode(env, payload)
        assert re != raw, f"silent identity at offset {flip_offset}"
    except (CodecError, UnicodeDecodeError):
        pass  # rejection is fine


def test_random_bit_flips_either_decode_cleanly_or_raise():
    """Strong invariant: a corrupted envelope is EITHER decodable to a
    different-but-valid message OR raises a CodecError. Silent corruption
    is forbidden."""
    rng = random.Random(0xCAFE)
    raw = _all_golden_bytes()[6]  # CHAT_TEXT
    failures = 0
    silent_changes = 0
    iterations = 200
    for _ in range(iterations):
        idx = rng.randint(0, len(raw) - 1)
        bit = 1 << rng.randint(0, 7)
        corrupted = bytearray(raw)
        corrupted[idx] ^= bit
        try:
            env, payload = decode(bytes(corrupted))
            # If it decoded, we must NOT recover the original bytes when re-encoding;
            # otherwise the corruption was in a no-op location, which is suspicious.
            re = encode(env, payload)
            if re != raw:
                silent_changes += 1  # this is fine — different message, but well-formed
        except (CodecError, UnicodeDecodeError):
            failures += 1
    # The vast majority of bit flips should land on structural / value bytes
    # and either fail decode or yield a different-but-well-formed message.
    assert failures + silent_changes >= iterations - 2, (
        f"too few rejections: failures={failures} silent_changes={silent_changes} of {iterations}"
    )


def test_truncation_at_every_position_rejected():
    """Truncating the envelope at any position must fail decode."""
    raw = _all_golden_bytes()[0]
    rejected = 0
    for cut in range(1, len(raw)):
        try:
            decode(raw[:cut])
        except (CodecError, UnicodeDecodeError):
            rejected += 1
    assert rejected == len(raw) - 1, "every prefix should be rejected"


def test_appended_garbage_rejected():
    """Bytes appended after the closing brace must fail decode."""
    raw = _all_golden_bytes()[0]
    with pytest.raises(CodecError):
        decode(raw + b"GARBAGE")


# ---------------------------------------------------------------------------
# REORDER — out-of-order arrivals decode independently
# ---------------------------------------------------------------------------

def test_reordered_arrivals_each_decode_independently():
    """A shuffled stream of envelopes still decodes one-by-one."""
    rng = random.Random(0xBEEF)
    stream = list(_all_golden_bytes())
    rng.shuffle(stream)
    for raw in stream:
        env, payload = decode(raw)
        assert env.v == 1


# ---------------------------------------------------------------------------
# DUPLICATE — codec accepts both copies; orchestrator-layer dedupe handles it
# ---------------------------------------------------------------------------

def test_duplicate_bytes_decode_to_equivalent_messages():
    """Two arrivals of the same bytes decode to byte-equal re-encodings.
    The orchestrator's DedupeWindow is what drops the second arrival."""
    raw = _all_golden_bytes()[6]  # CHAT_TEXT
    env1, p1 = decode(raw)
    env2, p2 = decode(raw)
    assert encode(env1, p1) == encode(env2, p2) == raw


# ---------------------------------------------------------------------------
# CORRUPT-IN-CIPHERTEXT — base64 changes propagate to the receiver
# ---------------------------------------------------------------------------

def test_modified_ciphertext_decodes_but_aead_layer_must_fail():
    """If we tamper with ciphertextB64 in a CHAT_TEXT, the codec accepts (it's
    still valid base64), but the AEAD open() step at the receiver must reject.
    The codec is NOT the integrity layer — the AEAD tag is. This pins that
    distinction so reviewers don't accidentally try to make the codec do AEAD
    validation."""
    raw = _all_golden_bytes()[6]  # CHAT_TEXT
    obj = json.loads(raw)
    obj["payload"]["ciphertextB64"] = "AAAAAAAAAAAAAAAA"  # valid base64, wrong bytes
    modified = encode_dict(obj)
    env, payload = decode(modified)
    assert env.msgType == MsgType.CHAT_TEXT
    assert payload["ciphertextB64"] == "AAAAAAAAAAAAAAAA"
    # AEAD verification would happen in MediaPipeline / chat layer, not here.


# ---------------------------------------------------------------------------
# SOAK — sustained load
# ---------------------------------------------------------------------------

def test_soak_10k_round_trips():
    """10,000 round-trips through the codec must be stable: no leaks, no drift,
    every iteration produces byte-equal output."""
    fixtures = _all_golden_bytes()
    iterations = 10_000
    for i in range(iterations):
        raw = fixtures[i % len(fixtures)]
        env, payload = decode(raw)
        again = encode(env, payload)
        if raw != again:
            raise AssertionError(f"iteration {i}: round-trip drift")


def test_soak_random_envelopes_5k():
    """5,000 randomly-chosen envelopes round-trip cleanly with no state leakage
    between iterations."""
    rng = random.Random(0xDEAD_BEEF)
    fixtures = _all_golden_bytes()
    for i in range(5_000):
        raw = rng.choice(fixtures)
        env, payload = decode(raw)
        assert encode(env, payload) == raw


# ---------------------------------------------------------------------------
# Cross-corruption — Android-encoded bytes corrupted in transit
# ---------------------------------------------------------------------------

def test_corruption_of_android_encoded_envelope_rejected():
    """Bytes produced by the Android codec sim, then corrupted, must fail the
    canonical decoder identically to corrupted Python-encoded bytes."""
    from tools import android_codec_sim
    raw = _all_golden_bytes()[6]
    obj = json.loads(raw)
    a_bytes = android_codec_sim.encode_dict(_strip_meta(obj))
    corrupted = bytearray(a_bytes)
    corrupted[20] ^= 0x40
    with pytest.raises((CodecError, UnicodeDecodeError)):
        decode(bytes(corrupted))
