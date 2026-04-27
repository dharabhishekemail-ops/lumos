
package com.lumos.session.deps

/** Minimal interface expected from core-protocol. */
interface ProtocolCodec {
    fun <T : Any> encode(envelope: TypedEnvelope<T>): ByteArray
    fun decode(bytes: ByteArray): TypedEnvelope<Any>
}

data class TypedEnvelope<T : Any>(
    val version: Int,
    val messageType: String,
    val messageId: String,
    val sessionId: String?,
    val payload: T
)

/** Minimal crypto facade expected from core-crypto-api. */
interface CryptoFacade {
    fun randomId(prefix: String): String
    fun sha256(bytes: ByteArray): ByteArray
}

/** Capability negotiation (from Phase 2). */
interface CapabilityNegotiator {
    fun selectCommon(local: Capabilities, remote: Capabilities): NegotiationResult
}

data class Capabilities(
    val protocolVersions: List<Int>,
    val transports: List<String>, // "BLE","WIFI","QR"
    val aeadSuites: List<String>,
    val kdfSuites: List<String>,
    val curves: List<String>
)

data class NegotiationResult(
    val ok: Boolean,
    val chosenProtocol: Int? = null,
    val chosenTransport: String? = null,
    val chosenAead: String? = null,
    val chosenKdf: String? = null,
    val chosenCurve: String? = null,
    val failureCode: String? = null
)
