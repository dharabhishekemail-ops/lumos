package com.lumos.core.billing

import android.app.Activity
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Production-grade BillingClient wrapper with:
 * - explicit connection lifecycle
 * - product catalog queries
 * - purchase flow launcher
 * - purchase updates stream
 *
 * IMPORTANT:
 * Receipt/server verification can be added later. For v1 local receipt verification and bounded entitlements
 * are handled via Signed Config + Play Billing purchase tokens.
 */
class BillingClientFacade(
    private val appContext: android.content.Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PurchasesUpdatedListener {

    private val updates = Channel<List<Purchase>>(Channel.BUFFERED)
    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            updates.trySend(purchases)
        } else {
            // Non-fatal: surface via logs/diagnostics, don't crash UI
        }
    }

    suspend fun connect(): Boolean = withContext(dispatcher) {
        if (client.isReady) {
            _connectionState.value = true
            return@withContext true
        }
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    _connectionState.value = ok
                    cont.resume(ok) {}
                }
                override fun onBillingServiceDisconnected() {
                    _connectionState.value = false
                }
            })
        }
    }

    fun purchaseUpdates(): Flow<List<Purchase>> = updates.receiveAsFlow()

    suspend fun queryProducts(productIds: List<String>, type: ProductType): List<ProductDetails> =
        withContext(dispatcher) {
            val productList = productIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(if (type == ProductType.SUBS) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            val res = client.queryProductDetails(params)
            if (res.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                res.productDetailsList ?: emptyList()
            } else emptyList()
        }

    fun launchPurchase(activity: Activity, productDetails: ProductDetails, offerToken: String? = null) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply {
                if (offerToken != null) setOfferToken(offerToken)
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        client.launchBillingFlow(activity, flowParams)
    }

    suspend fun acknowledgeIfNeeded(purchase: Purchase): Boolean = withContext(dispatcher) {
        if (purchase.isAcknowledged) return@withContext true
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        val res = client.acknowledgePurchase(params)
        res.responseCode == BillingClient.BillingResponseCode.OK
    }

    suspend fun queryActivePurchases(): List<Purchase> = withContext(dispatcher) {
        val inapp = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build())
        val subs = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).build())
        (inapp.purchasesList ?: emptyList()) + (subs.purchasesList ?: emptyList())
    }
}
