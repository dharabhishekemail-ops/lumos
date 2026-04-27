package com.lumos.core.media

import com.lumos.core.crypto.api.Aead
import java.util.concurrent.atomic.AtomicInteger

/**
 * MediaSender
 * - chunks plaintext
 * - encrypts each chunk with per-transfer AEAD
 * - emits encrypted chunks with deterministic AAD (msgId + mime + chunkIndex)
 *
 * Retry/resume is orchestrator-owned (Phase 6): sender is stateless except a monotonic msg counter.
 */
class MediaSender(
    private val aead: Aead,
    private val mime: String,
    private val msgIdCounter: AtomicInteger = AtomicInteger(1),
) {
    fun encryptChunks(desc: MediaDescriptor, plain: ByteArray): List<MediaChunkCipher> {
        if (plain.size.toLong() != desc.sizeBytes) return emptyList()
        if (MediaIntegrity.sha256Hex(plain) != desc.sha256Hex) return emptyList()

        val chunks = MediaChunker.chunk(plain, desc.chunkSize)
        val total = chunks.size
        return chunks.mapIndexed { idx, p ->
            val msgId = "m_%08d".format(msgIdCounter.getAndIncrement())
            val aad = "LUMOS_MEDIA_AAD_V1|$msgId|${desc.transferId}|$mime|$idx|$total".toByteArray(Charsets.UTF_8)
            val nonce = aead.newNonce()
            val ct = aead.encrypt(nonce, p, aad)
            MediaChunkCipher(desc.transferId, idx, total, nonce, ct, aad)
        }
    }
}
