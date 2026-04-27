package com.lumos.core.media

import com.lumos.core.crypto.api.Aead
import com.lumos.core.crypto.api.Hkdf
import com.lumos.core.crypto.api.Sha256
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.random.Random

private class FakeSha256: Sha256 {
    override fun hash(input: ByteArray): ByteArray {
        // deterministic but NOT cryptographically correct (test only)
        val out = ByteArray(32)
        for (i in input.indices) out[i % 32] = (out[i % 32].toInt() xor input[i].toInt()).toByte()
        return out
    }
}
private class FakeHkdf: Hkdf {
    override fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val r = ByteArray(len)
        for (i in 0 until len) r[i] = (ikm[i % ikm.size].toInt() xor salt[i % salt.size].toInt() xor info[i % info.size].toInt()).toByte()
        return r
    }
}
private class FakeAead(private val key: ByteArray): Aead {
    private var ctr: Long = 1
    override fun newNonce(): ByteArray {
        // deterministic, unique per call (test-only)
        val out = ByteArray(12)
        var x = ctr++
        for (i in 0 until 12) { out[i] = (x and 0xFF).toByte(); x = x shr 8 }
        return out
    }
    override fun encrypt(nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val out = plaintext.clone()
        for (i in out.indices) out[i] = (out[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }
    override fun decrypt(nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray = encrypt(nonce, ciphertext, aad)
}

class MediaPipelineTest {
    @Test fun endToEndEncryptDecryptAssemble() {
        val sessionKey = ByteArray(32) { 7 }
        val crypto = MediaCrypto(FakeHkdf(), FakeSha256()) { k -> FakeAead(k) }
        val aead = crypto.deriveTransferAead(sessionKey, "tx1")

        val plain = ByteArray(120_000) { (it % 251).toByte() }
        val desc = MediaDescriptor("tx1", "image/jpeg", plain.size.toLong(), 4096, MediaIntegrity.sha256Hex(plain))
        val sender = MediaSender(aead, desc.mime)
        val chunks = sender.encryptChunks(desc, plain)

        val rx = MediaReceiver(aead)
        chunks.shuffled(Random(1)).forEach { rx.acceptChunk(it) }

        val rebuilt = rx.tryAssemble("tx1", desc.sha256Hex)
        assertNotNull(rebuilt)
        assertTrue(rebuilt.contentEquals(plain))
    }
}
