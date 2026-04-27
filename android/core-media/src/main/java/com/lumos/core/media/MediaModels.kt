package com.lumos.core.media

data class MediaDescriptor(
    val transferId: String,
    val mime: String,
    val sizeBytes: Long,
    val chunkSize: Int,
    val sha256Hex: String
)

data class MediaChunkCipher(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val aad: ByteArray
)

data class MediaAck(
    val transferId: String,
    val highestContiguousAck: Int,
    val missing: IntArray,
    val receivedCount: Int
)
