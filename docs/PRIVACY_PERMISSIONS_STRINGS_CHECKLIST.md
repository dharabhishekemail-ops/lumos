# Privacy + Permissions Strings Checklist (Android/iOS)

## Android (Manifest + runtime)
Bluetooth / Nearby (API-dependent):
- android.permission.BLUETOOTH_SCAN (runtime)
- android.permission.BLUETOOTH_CONNECT (runtime)
- android.permission.BLUETOOTH_ADVERTISE (runtime)
- Location permission should be avoided for BLE if possible, but may be required on some devices/OS; ensure rationale.

Wi-Fi/Local network:
- android.permission.ACCESS_WIFI_STATE
- android.permission.CHANGE_WIFI_STATE (only if needed)
- android.permission.INTERNET

Camera (voucher QR scan):
- android.permission.CAMERA (runtime)

Notifications:
- android.permission.POST_NOTIFICATIONS (API 33+ runtime)

## iOS (Info.plist keys)
- NSBluetoothAlwaysUsageDescription
- NSLocalNetworkUsageDescription (Bonjour)
- NSBonjourServices (service types)
- NSCameraUsageDescription (voucher QR)
- NSPhotoLibraryAddUsageDescription (if saving media)
- NSUserTrackingUsageDescription should be avoided unless explicitly doing tracking (out of scope).

## Copy Rules
- Explain **offline-first local discovery** and what is shared locally.
- Default to privacy-first toggles OFF unless required.
- No claims of preventing screenshots; only warnings.

## Validation
- Deny each permission and verify user-friendly degraded states.
- Revoke permissions while running; ensure no crash.
