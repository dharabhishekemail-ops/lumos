package com.lumos.core.common

@JvmInline value class SessionId(val value: String)
@JvmInline value class MessageId(val value: String)
@JvmInline value class DeviceEphemeralId(val value: String)

enum class TransportKind { BLE, WIFI_LOCAL, QR_BOOTSTRAP }
