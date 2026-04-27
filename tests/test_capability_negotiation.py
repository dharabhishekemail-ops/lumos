"""Capability negotiation invariants — verified at the protocol level (Python).

These tests pin the rules that the Android Kotlin and iOS Swift CapabilityNegotiator
implementations must obey. If either platform diverges, those platform-specific
unit tests would fail; this file pins the spec contract that the platforms
share.

Per Lumos Protocol & Interop Contract v1.0 §2 and Crypto Spec v1.0 §3.
"""
from __future__ import annotations

import pytest


# Canonical local capability set (mirror of LocalCapabilities.defaultLocal in iOS
# and the Android equivalent). Order = preference.
LOCAL_VERSIONS = [1]
LOCAL_TRANSPORTS = ["BLE", "WIFI", "QR"]
LOCAL_AEAD = ["CHACHA20_POLY1305", "AES_256_GCM"]
LOCAL_KDF = ["HKDF_SHA256"]
LOCAL_CURVES = ["X25519"]
LOCAL_FEATURES = ["CHAT_TEXT", "CHAT_MEDIA_PHOTO", "VOUCHER_QR"]


def negotiate(local_versions, local_transports, local_aead, local_kdf, local_curves, local_features,
              remote_versions, remote_transports, remote_aead, remote_kdf, remote_curves, remote_features):
    """Reference negotiation algorithm shared by both platforms."""
    mutual_v = sorted(set(local_versions) & set(remote_versions), reverse=True)
    if not mutual_v: return ("incompatibleVersion",)
    v = mutual_v[0]
    transport = next((t for t in local_transports if t in remote_transports), None)
    if transport is None: return ("noCommonTransport",)
    aead = next((a for a in local_aead if a in remote_aead), None)
    if aead is None: return ("noCommonAead",)
    kdf = next((k for k in local_kdf if k in remote_kdf), None)
    if kdf is None: return ("noCommonKdf",)
    curve = next((c for c in local_curves if c in remote_curves), None)
    if curve is None: return ("noCommonCurve",)
    features = [f for f in local_features if f in remote_features]
    return ("ok", v, transport, aead, kdf, curve, features)


def test_happy_path():
    res = negotiate(LOCAL_VERSIONS, LOCAL_TRANSPORTS, LOCAL_AEAD, LOCAL_KDF, LOCAL_CURVES, LOCAL_FEATURES,
                    [1], ["BLE","WIFI","QR"], ["CHACHA20_POLY1305","AES_256_GCM"],
                    ["HKDF_SHA256"], ["X25519"], ["CHAT_TEXT","CHAT_MEDIA_PHOTO","VOUCHER_QR"])
    assert res[0] == "ok"
    assert res[1] == 1
    assert res[2] == "BLE"      # local prefers BLE first
    assert res[3] == "CHACHA20_POLY1305"  # local prefers ChaCha first
    assert res[6] == ["CHAT_TEXT","CHAT_MEDIA_PHOTO","VOUCHER_QR"]


def test_incompatible_version():
    res = negotiate(LOCAL_VERSIONS, LOCAL_TRANSPORTS, LOCAL_AEAD, LOCAL_KDF, LOCAL_CURVES, LOCAL_FEATURES,
                    [2,3], ["BLE"], ["CHACHA20_POLY1305"], ["HKDF_SHA256"], ["X25519"], [])
    assert res == ("incompatibleVersion",)


def test_no_common_transport():
    res = negotiate(LOCAL_VERSIONS, LOCAL_TRANSPORTS, LOCAL_AEAD, LOCAL_KDF, LOCAL_CURVES, LOCAL_FEATURES,
                    [1], [], ["CHACHA20_POLY1305"], ["HKDF_SHA256"], ["X25519"], [])
    assert res == ("noCommonTransport",)


def test_transport_preference_picks_wifi_when_no_ble():
    res = negotiate(LOCAL_VERSIONS, LOCAL_TRANSPORTS, LOCAL_AEAD, LOCAL_KDF, LOCAL_CURVES, LOCAL_FEATURES,
                    [1], ["QR","WIFI"], ["CHACHA20_POLY1305"], ["HKDF_SHA256"], ["X25519"], [])
    assert res[2] == "WIFI"
