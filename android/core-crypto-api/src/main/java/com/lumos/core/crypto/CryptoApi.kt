package com.lumos.core.crypto

data class PublicKeyBytes(val bytes: ByteArray)
data class PrivateKeyHandle(val id: String)
data class SharedSecret(val bytes: ByteArray)
data class Ciphertext(val bytes: ByteArray)
data class Nonce(val bytes: ByteArray)
data class AeadResult(val ciphertext: Ciphertext, val nonce: Nonce, val tag: ByteArray)

interface IdentityKeyStore {
    fun getOrCreateIdentityKeyPair(): Pair<PublicKeyBytes, PrivateKeyHandle>
    fun resetIdentityKeys(): Boolean
}

interface SessionKeyAgreement {
    fun deriveSharedSecret(localPrivate: PrivateKeyHandle, remotePublic: PublicKeyBytes): SharedSecret
}

interface Kdf {
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray
}

interface AeadCipher {
    fun encrypt(key: ByteArray, nonce: Nonce, aad: ByteArray, plaintext: ByteArray): AeadResult
    fun decrypt(key: ByteArray, nonce: Nonce, aad: ByteArray, ciphertext: ByteArray, tag: ByteArray): Result<ByteArray>
}

interface RatchetEngine {
    fun initialize(rootKey: ByteArray, transcriptHash: ByteArray): RatchetSession
}

interface RatchetSession {
    fun nextOutboundMessageKey(): ByteArray
    fun tryInboundMessageKey(counter: Long): Result<ByteArray>
    fun snapshot(): ByteArray
}
