package com.lumos.core.media

import com.lumos.core.crypto.api.Aead
import java.util.BitSet

/**
 * MediaReceiver
 * - accepts encrypted chunks in any order
 * - decrypts and stores in memory (Phase 6) and can be persisted by caller
 * - emits ACK bitmap state
 */
class MediaReceiver(
    private val aead: Aead,
) {
    private data class InFlight(
        val total: Int,
        val chunks: Array<ByteArray?>,
        val received: BitSet
    )

    private val inflight = mutableMapOf<String, InFlight>()

    fun acceptChunk(chunk: MediaChunkCipher): MediaAck {
        val st = inflight.getOrPut(chunk.transferId) {
            InFlight(chunk.totalChunks, arrayOfNulls(chunk.totalChunks), BitSet(chunk.totalChunks))
        }
        if (st.total != chunk.totalChunks) {
            // Ignore malformed chunk (peer input). Do not crash.
            return MediaAck(chunk.transferId, -1, intArrayOf(), st.received.cardinality())
        }
        if (chunk.chunkIndex !in 0 until st.total) {
            return MediaAck(chunk.transferId, -1, intArrayOf(), st.received.cardinality())
        }

        val plain = aead.decrypt(chunk.nonce, chunk.ciphertext, chunk.aad)
        st.chunks[chunk.chunkIndex] = plain
        st.received.set(chunk.chunkIndex)

        val highest = computeHighestContiguous(st.received)
        val missing = computeMissingUpTo(st.received, highest + 1, minOf(st.total, highest + 1 + 16))
        val receivedCount = st.received.cardinality()

        return MediaAck(chunk.transferId, highest, missing, receivedCount)
    }

    fun tryAssemble(transferId: String, expectedSha256Hex: String): ByteArray? {
        val st = inflight[transferId] ?: return null
        if (st.received.cardinality() != st.total) return null
        val chunks = st.chunks.filterNotNull()
        if (chunks.size != st.total) return null
        val out = ByteArray(chunks.sumOf { it.size })
        var off = 0
        for (i in 0 until st.total) {
            val c = st.chunks[i] ?: return null
            System.arraycopy(c, 0, out, off, c.size)
            off += c.size
        }
        if (MediaIntegrity.sha256Hex(out) != expectedSha256Hex) {
            // Fail-safe: discard corrupted transfer
            inflight.remove(transferId)
            return null
        }
        inflight.remove(transferId)
        return out
    }

    private fun computeHighestContiguous(bs: BitSet): Int {
        var i = 0
        while (bs.get(i)) i++
        return i - 1
    }

    private fun computeMissingUpTo(bs: BitSet, start: Int, end: Int): IntArray {
        val miss = ArrayList<Int>()
        for (i in start until end) if (!bs.get(i)) miss.add(i)
        return miss.toIntArray()
    }
}