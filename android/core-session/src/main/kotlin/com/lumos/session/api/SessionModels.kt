
package com.lumos.session.api

import java.time.Instant

/** High-level session lifecycle state (transport-agnostic). */
sealed class SessionState {
    data object Idle : SessionState()
    data class Discovering(val transportHint: TransportHint?) : SessionState()
    data class Connecting(val peer: PeerRef, val transport: TransportKind) : SessionState()
    data class Negotiating(val peer: PeerRef, val transport: TransportKind) : SessionState()
    data class Authenticated(val peer: PeerRef, val transport: TransportKind, val sessionId: String) : SessionState()
    data class Active(val peer: PeerRef, val transport: TransportKind, val sessionId: String) : SessionState()
    data class Migrating(val peer: PeerRef, val from: TransportKind, val to: TransportKind, val sessionId: String) : SessionState()
    data class Failed(val reason: FailureReason, val at: Instant = Instant.now()) : SessionState()
    data object Terminated : SessionState()
}

enum class TransportKind { BLE, WIFI, QR }

data class TransportHint(val preferred: List<TransportKind>)

data class PeerRef(val peerId: String, val displayAlias: String? = null)

enum class FailureReason {
    NO_COMMON_CAPABILITIES,
    TRANSPORT_ERROR,
    TIMEOUT,
    AUTH_FAILED,
    PROTOCOL_ERROR,
    CONFIG_INVALID,
    CRYPTO_ERROR
}

/** Message delivery status for UI. */
enum class DeliveryStatus { QUEUED, SENT, DELIVERED, FAILED, RETRYING }

/** A chat message as seen by the app layer. */
data class ChatMessage(
    val messageId: String,
    val sessionId: String,
    val fromPeerId: String,
    val toPeerId: String,
    val timestampMs: Long,
    val text: String,
    val status: DeliveryStatus
)
