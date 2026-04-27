# Lumos Phase 4 (v0.4) - Real Transport Integration Artifacts

Adds concrete transport implementations per DDS/SAS:
- Android BLE GATT adapter (central + peripheral roles) with framing and chunking.
- Android Wi-Fi local TCP adapter using NSD discovery + framing.
- Android QR bootstrap encoder/decoder for manual fallback.
- iOS BLE adapter using CoreBluetooth (central + peripheral) with writeWithoutResponse pacing hook.
- iOS Wi-Fi adapter using Network.framework + Bonjour.
- iOS QR bootstrap encoder/decoder.

This phase intentionally focuses on transport plumbing + lifecycle surfaces.
Session/orchestrator integration + UI flows follow in Phase 5+.
