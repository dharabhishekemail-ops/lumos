package com.lumos.core.crypto.api

interface Sha256 { fun hash(input: ByteArray): ByteArray }
interface Hkdf { fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray }
interface Aead {
    fun newNonce(): ByteArray
    fun encrypt(nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    fun decrypt(nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
}
