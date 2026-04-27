package com.lumos.feature.voucher

data class Voucher(
    val voucherId: String,
    val venueId: String,
    val offerTitle: String,
    val expiresEpochMs: Long,
    val signatureB64: String,
)
