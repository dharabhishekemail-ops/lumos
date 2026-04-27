package com.lumos.transport.ble

import java.util.UUID

/**
 * Fixed UUIDs (v1.0 baseline). Change requires protocol revision.
 * These UUIDs are used for a GATT service that carries framed protocol bytes.
 */
object BleUuids {
    val SERVICE: UUID = UUID.fromString("8f6d2aa0-7f47-4b4c-8f3d-2a1d27d3b6e1")
    val CHAR_RX: UUID = UUID.fromString("8f6d2aa1-7f47-4b4c-8f3d-2a1d27d3b6e1") // central -> peripheral writes
    val CHAR_TX: UUID = UUID.fromString("8f6d2aa2-7f47-4b4c-8f3d-2a1d27d3b6e1") // peripheral -> central notifies
}
