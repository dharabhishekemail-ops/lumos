package com.lumos.core.billing

import com.android.billingclient.api.Purchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Local entitlements computed from:
 * - Valid purchases (Play Billing)
 * - Signed config pack (SKU visibility/labels and entitlement mapping)
 *
 * The mapping remains bounded and safe, and can be updated via signed config without code changes.
 */
class EntitlementManager(
    private val mapping: SkuEntitlementMapping,
) {
    private val _entitlements = MutableStateFlow(Entitlements(false, 0, System.currentTimeMillis()))
    val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    fun recompute(purchases: List<Purchase>) {
        val ent = mapping.compute(purchases)
        _entitlements.value = ent.copy(lastUpdatedEpochMs = System.currentTimeMillis())
    }
}

interface SkuEntitlementMapping {
    fun compute(purchases: List<Purchase>): Entitlements
}

/** Default mapping - can be overridden by config-driven mapping rules. */
class DefaultSkuEntitlementMapping(
    private val premiumSkuIds: Set<String>,
    private val boostSkuId: String,
    private val boostCountPerPurchase: Int = 1,
) : SkuEntitlementMapping {
    override fun compute(purchases: List<Purchase>): Entitlements {
        val activeSkus = purchases.flatMap { it.products }.toSet()
        val premium = activeSkus.any { premiumSkuIds.contains(it) }
        val boosts = purchases.count { it.products.contains(boostSkuId) } * boostCountPerPurchase
        return Entitlements(premium, boosts, System.currentTimeMillis())
    }
}
