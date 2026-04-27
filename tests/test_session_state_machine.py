"""Orchestrator-level replay detection.

The codec accepts replay-shaped fixtures as well-formed envelopes — replay
detection is a SESSION-layer concern (Protocol Contract §5: 64-message
sliding window indexed by messageId).

This suite verifies the dedupe window logic that every implementation
(Android, iOS, reference) is required to share.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parent.parent


class DedupeWindow:
    """Reference implementation of the replay window. Mirrors:
       - iOS:     LumosKit/Sources/LumosSession/SessionOrchestrator.swift::DedupeWindow
       - Android: android/feature-session/.../SessionOrchestrator.kt::DedupeWindow
    """
    def __init__(self, capacity: int = 64) -> None:
        assert capacity > 0
        self.capacity = capacity
        self.seen: list[str] = []

    def accept_if_fresh(self, message_id: str) -> bool:
        if message_id in self.seen:
            return False
        if len(self.seen) == self.capacity:
            self.seen.pop(0)
        self.seen.append(message_id)
        return True


def test_first_seen_accepted():
    w = DedupeWindow()
    assert w.accept_if_fresh("m_msg00001x") is True


def test_immediate_replay_rejected():
    w = DedupeWindow()
    w.accept_if_fresh("m_msg00001x")
    assert w.accept_if_fresh("m_msg00001x") is False


def test_distinct_ids_all_accepted():
    w = DedupeWindow()
    for i in range(64):
        assert w.accept_if_fresh(f"m_msg{i:08x}") is True


def test_evicted_id_can_reappear():
    """Once the oldest messageId falls out of the 64-message window, it can be reused."""
    w = DedupeWindow(capacity=4)
    assert w.accept_if_fresh("a") is True
    assert w.accept_if_fresh("b") is True
    assert w.accept_if_fresh("c") is True
    assert w.accept_if_fresh("d") is True
    assert w.accept_if_fresh("e") is True   # this evicts "a"
    assert w.accept_if_fresh("a") is True   # "a" is fresh again


def test_replay_fixture_is_a_well_formed_envelope_but_orchestrator_drops_it():
    """The neg09 fixture has a valid envelope. The orchestrator-layer dedupe
    is what rejects it on second arrival."""
    fixture = json.loads((ROOT / "fixtures" / "negative" / "neg09_replay_duplicate.json").read_text())
    assert fixture["_meta"]["purpose"] == "orchestrator-replay"
    msg_id = fixture["envelope"]["messageId"]

    w = DedupeWindow()
    # First arrival: accepted.
    assert w.accept_if_fresh(msg_id) is True
    # Replay: rejected.
    assert w.accept_if_fresh(msg_id) is False
