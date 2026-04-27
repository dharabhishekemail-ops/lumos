package com.lumos.core.billing

data class Sku(
    val productId: String,
    val type: ProductType,
    val title: String,
    val description: String,
    val price: String,
)

enum class ProductType { INAPP, SUBS }

sealed class PurchaseState {
    data object NotPurchased : PurchaseState()
    data class Purchased(val productId: String, val purchaseToken: String) : PurchaseState()
}

data class Entitlements(
    val premiumUnlocked: Boolean,
    val boosts: Int,
    val lastUpdatedEpochMs: Long,
)
