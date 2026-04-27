package com.lumos.core.media

import com.lumos.core.crypto.api.Aead
import com.lumos.core.crypto.api.Hkdf
import com.lumos.core.crypto.api.Sha256

/**
 * Derives a per-transfer AEAD key from the session root key using HKDF-SHA256.
 *
 * Key schedule (Phase 6):
 *   media_key = HKDF(root=sessionKey, salt=sha256("LUMOS_MEDIA_SALT"||transferId), info="LUMOS_MEDIA_AEAD_V1", len=32)
 */
class MediaCrypto(
    private val hkdf: Hkdf,
    private val sha256: Sha256,
    private val aeadFactory: (ByteArray) -> Aead
) {
    fun deriveTransferAead(sessionKey: ByteArray, transferId: String): Aead {
        val salt = sha256.hash(("LUMOS_MEDIA_SALT:$transferId").toByteArray(Charsets.UTF_8))
        val key = hkdf.deriveKey(sessionKey, salt, "LUMOS_MEDIA_AEAD_V1".toByteArray(Charsets.UTF_8), 32)
        return aeadFactory(key)
    }
}
