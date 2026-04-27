package com.lumos.transport

import com.lumos.protocol.TransportKind

/**
 * TransportAdapter is the ONLY surface the session/orchestrator talks to.
 * Concrete implementations: BLE GATT, Wi-Fi TCP (Bonjour/NSD), QR bootstrap.
 *
 * Design goals (from DDS/SAS):
 * - transport-agnostic app layer
 * - deterministic retries/migration
 * - clean lifecycle (start/stop) for venue sessions
 * - testability via fakes and fault injection
 */
interface TransportAdapter {
    val id: String
    val kind: TransportKind
    fun setListener(listener: TransportListener?)
    fun start(mode: StartMode)
    fun stop()
    /** Send a single framed message (codec output). Adapter is responsible for chunking/framing for the medium. */
    fun send(peerId: String, bytes: ByteArray)
    fun peers(): Set<String>
}

enum class StartMode { Advertise, Discover, Session }

/** Callbacks are serialized by adapter (single-threaded) to avoid re-entrancy into orchestrator. */
interface TransportListener {
    fun onPeerDiscovered(peer: PeerInfo) {}
    fun onPeerLost(peerId: String) {}
    fun onConnected(peerId: String) {}
    fun onDisconnected(peerId: String, reason: DisconnectReason) {}
    fun onBytesReceived(peerId: String, bytes: ByteArray)
    fun onAdapterError(error: AdapterError) {}
}

data class PeerInfo(
    val peerId: String,
    val rssi: Int? = null,
    val hint: String? = null, // e.g. truncated alias/tag for UI preview if allowed
)

enum class DisconnectReason { Remote, Local, Timeout, RadioLost, ProtocolError, Unknown }

sealed class AdapterError(val code: Code, val message: String, val cause: Throwable? = null) {
    enum class Code {
        PermissionDenied,
        RadioDisabled,
        NotSupported,
        IoError,
        Busy,
        Internal,
    }
}

/**
 * A simple length-prefixed framing used across Wi-Fi and BLE.
 * - 2-byte unsigned big-endian length prefix (0..65535)
 * - followed by payload bytes
 *
 * BLE GATT will additionally chunk frames to MTU-sized pieces.
 */
object FrameCodec {
    fun encode(payload: ByteArray): ByteArray {
        if (payload.size > 0xFFFF) {
            // Do not crash on oversized payload (can be peer/config-driven). Caller must handle empty result.
            return ByteArray(0)
        }
        val out = ByteArray(2 + payload.size)
        out[0] = ((payload.size ushr 8) and 0xFF).toByte()
        out[1] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 2, payload.size)
        return out
    }

    /** Returns (frames, remainder). */
    fun decodeMany(buffer: ByteArray): Pair<List<ByteArray>, ByteArray> {
        var i = 0
        val frames = mutableListOf<ByteArray>()
        while (i + 2 <= buffer.size) {
            val len = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            if (i + 2 + len > buffer.size) break
            val payload = buffer.copyOfRange(i + 2, i + 2 + len)
            frames += payload
            i += 2 + len
        }
        return frames to buffer.copyOfRange(i, buffer.size)
    }
}
