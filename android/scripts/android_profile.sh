#!/usr/bin/env bash
set -euo pipefail

# Android profiling helper for Lumos (requires adb).
# Usage: ./android_profile.sh <package.name>

PKG="${1:-com.lumos.app}"

echo "[1] Clearing logcat..."
adb logcat -c || true

echo "[2] Starting simple startup timing capture..."
adb shell am force-stop "$PKG" || true
START=$(date +%s%3N)
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 2
END=$(date +%s%3N)
echo "Approx startup wall time: $((END-START)) ms"

echo "[3] Collecting bugreport (may take time)..."
adb bugreport "lumos_bugreport_$(date +%Y%m%d_%H%M%S).zip" || true

echo "[4] Done."
