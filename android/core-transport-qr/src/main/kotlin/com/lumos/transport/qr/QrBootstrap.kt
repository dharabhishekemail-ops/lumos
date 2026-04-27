package com.lumos.transport.qr

import android.util.Base64
import org.json.JSONObject

/**
 * QR fallback is a bootstrap, not a transport.
 * It carries a minimal signed/unencrypted bootstrap blob that allows peers to:
 * - agree on protocol version/capabilities
 * - exchange ephemeral session init material (e.g., prekey, sessionId)
 *
 * The payload MUST NOT contain PII and MUST be bounded in size.
 */
data class QrBootstrapPayload(
    val v: Int,
    val sessionId: String,
    val deviceEphemeralPubKeyB64: String,
    val capabilitiesHashHex: String,
    val issuedAtMs: Long,
    val ttlMs: Long
)

object QrBootstrapCodec {
    fun encode(p: QrBootstrapPayload): String {
        val jo = JSONObject()
            .put("v", p.v)
            .put("sid", p.sessionId)
            .put("epk", p.deviceEphemeralPubKeyB64)
            .put("cap", p.capabilitiesHashHex)
            .put("iat", p.issuedAtMs)
            .put("ttl", p.ttlMs)
        val raw = jo.toString().toByteArray(Charsets.UTF_8)
        // URL-safe no-wrap
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun decode(b64: String): QrBootstrapPayload {
        val raw = Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP)
        val jo = JSONObject(String(raw, Charsets.UTF_8))
        return QrBootstrapPayload(
            v = jo.getInt("v"),
            sessionId = jo.getString("sid"),
            deviceEphemeralPubKeyB64 = jo.getString("epk"),
            capabilitiesHashHex = jo.getString("cap"),
            issuedAtMs = jo.getLong("iat"),
            ttlMs = jo.getLong("ttl"),
        )
    }
}
