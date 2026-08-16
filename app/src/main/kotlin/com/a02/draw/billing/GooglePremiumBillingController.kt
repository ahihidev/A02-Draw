package com.a02.draw.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.core.ui.billing.PremiumBillingController
import com.a02.draw.core.ui.billing.PremiumBillingState
import com.a02.draw.core.ui.billing.PremiumCatalogStatus
import com.a02.draw.core.ui.billing.PremiumOffer
import com.a02.draw.core.ui.billing.PremiumProductType
import com.a02.draw.core.ui.billing.PremiumPurchaseStatus
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePremiumBillingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: BillingProductCatalog,
    private val verifier: PurchaseVerificationGateway,
    private val premiumEntitlement: PremiumEntitlementController,
) : PremiumBillingController, PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PremiumBillingState())
    override val state: StateFlow<PremiumBillingState> = _state.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private var catalogGeneration = 0L
    private var purchaseGeneration = 0L
    private var connecting = false
    private var connectionTimeoutJob: Job? = null
    private val connectionCallbacks = mutableListOf<(BillingResult?) -> Unit>()

    override fun refreshProducts() {
        val requestGeneration = ++catalogGeneration
        _state.value = _state.value.copy(
            catalogStatus = PremiumCatalogStatus.LOADING,
            unfetchedProductIds = emptySet(),
            purchaseStatus = PremiumPurchaseStatus.Idle,
        )
        if (catalog.allIds.isEmpty()) {
            _state.value = _state.value.copy(
                catalogStatus = PremiumCatalogStatus.EMPTY,
                offers = emptyList(),
                selectedOfferKey = null,
            )
            return
        }
        withConnectedClient { connectionError ->
            if (requestGeneration != catalogGeneration) return@withConnectedClient
            if (connectionError != null) {
                _state.value = _state.value.copy(
                    catalogStatus = PremiumCatalogStatus.ERROR,
                    offers = emptyList(),
                    unfetchedProductIds = catalog.allIds,
                    selectedOfferKey = null,
                )
                return@withConnectedClient
            }
            queryConfiguredProducts(requestGeneration) { details, unfetched, allQueriesFailed ->
                if (requestGeneration != catalogGeneration) return@queryConfiguredProducts
                val handles = PremiumProductDetailsMapper.map(details, catalog)
                val offers = handles.map(BillingOfferHandle::offer)
                val previousSelection = _state.value.selectedOfferKey
                _state.value = _state.value.copy(
                    catalogStatus = when {
                        offers.isNotEmpty() -> PremiumCatalogStatus.READY
                        allQueriesFailed -> PremiumCatalogStatus.ERROR
                        else -> PremiumCatalogStatus.EMPTY
                    },
                    offers = offers,
                    unfetchedProductIds = unfetched,
                    selectedOfferKey = previousSelection?.takeIf { key ->
                        offers.any { it.key == key }
                    } ?: offers.firstOrNull()?.key,
                )
            }
        }
    }

    override fun selectOffer(offerKey: String) {
        if (_state.value.offers.any { it.key == offerKey }) {
            if (_state.value.purchaseStatus == PremiumPurchaseStatus.Launching) {
                purchaseGeneration++
            }
            _state.value = _state.value.copy(
                selectedOfferKey = offerKey,
                purchaseStatus = PremiumPurchaseStatus.Idle,
            )
        }
    }

    override fun launchPurchase(activity: Activity, offerKey: String) {
        val selected = _state.value.offers.firstOrNull { it.key == offerKey } ?: return
        val requestGeneration = ++purchaseGeneration
        _state.value = _state.value.copy(purchaseStatus = PremiumPurchaseStatus.Launching)
        withConnectedClient { connectionError ->
            if (requestGeneration != purchaseGeneration) return@withConnectedClient
            if (connectionError != null) {
                failPurchase()
                return@withConnectedClient
            }
            querySingleProduct(selected) { details ->
                if (requestGeneration != purchaseGeneration) return@querySingleProduct
                val freshHandle = PremiumProductDetailsMapper.map(details, catalog)
                    .firstOrNull { it.offer.key == offerKey }
                if (freshHandle == null) {
                    failPurchase()
                    return@querySingleProduct
                }
                val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(freshHandle.productDetails)
                    .apply {
                        freshHandle.offerToken?.takeIf(String::isNotBlank)?.let(::setOfferToken)
                    }
                    .build()
                val result = billingClient.launchBillingFlow(
                    activity,
                    BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productParams))
                        .build(),
                )
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    failPurchase()
                }
            }
        }
    }

    override fun restorePurchases() {
        refreshProducts()
        queryAndVerifyPurchases(showResult = true)
    }

    override fun synchronizeEntitlement() {
        queryAndVerifyPurchases(showResult = false)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> verifyPurchases(purchases.orEmpty(), true)
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(purchaseStatus = PremiumPurchaseStatus.Canceled)
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryAndVerifyPurchases(true)
            else -> failPurchase()
        }
    }

    private fun queryConfiguredProducts(
        requestGeneration: Long,
        onComplete: (List<ProductDetails>, Set<String>, Boolean) -> Unit,
    ) {
        val details = mutableListOf<ProductDetails>()
        val unfetched = mutableSetOf<String>()
        val configuredQueries = buildList {
            if (catalog.subscriptionIds.isNotEmpty()) {
                add(BillingClient.ProductType.SUBS to catalog.subscriptionIds)
            }
            if (catalog.inAppIds.isNotEmpty()) {
                add(BillingClient.ProductType.INAPP to catalog.inAppIds)
            }
        }
        var remaining = configuredQueries.size
        var failures = 0
        fun completeOne(result: ProductQueryResult) {
            if (requestGeneration != catalogGeneration) return
            details += result.details
            unfetched += result.unfetchedIds
            if (!result.success) failures++
            remaining--
            if (remaining == 0) {
                unfetched += catalog.allIds - details.map(ProductDetails::getProductId).toSet()
                onComplete(details, unfetched, failures == configuredQueries.size)
            }
        }
        configuredQueries.forEach { (type, ids) -> queryProducts(type, ids, ::completeOne) }
    }

    private fun querySingleProduct(
        offer: PremiumOffer,
        onComplete: (List<ProductDetails>) -> Unit,
    ) {
        val type = if (offer.productType == PremiumProductType.SUBSCRIPTION) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
        queryProducts(type, listOf(offer.productId)) { result ->
            onComplete(if (result.success) result.details else emptyList())
        }
    }

    private fun queryProducts(
        productType: String,
        productIds: List<String>,
        onComplete: (ProductQueryResult) -> Unit,
    ) {
        if (productIds.isEmpty()) {
            onComplete(ProductQueryResult(success = true))
            return
        }
        val products = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { billingResult, result ->
            val success = billingResult.responseCode == BillingClient.BillingResponseCode.OK
            if (!success) {
                Log.w(
                    TAG,
                    "Product query failed type=$productType package=${context.packageName} " +
                            "response=${billingResult.responseCode} message=${billingResult.debugMessage}",
                )
            }
            result.unfetchedProductList.forEach { product ->
                Log.w(
                    TAG,
                    "Product not fetched id=${product.productId} type=${product.productType} " +
                            "reason=${product.statusCode.toUnfetchedReason()} " +
                            "package=${context.packageName}",
                )
            }
            result.productDetailsList.forEach { product ->
                Log.d(TAG, "Product fetched id=${product.productId} type=${product.productType}")
            }
            onComplete(
                ProductQueryResult(
                    success = success,
                    details = if (success) result.productDetailsList else emptyList(),
                    unfetchedIds = if (success) {
                        result.unfetchedProductList.mapTo(mutableSetOf()) { it.productId }
                    } else {
                        productIds.toSet()
                    },
                ),
            )
        }
    }

    private fun queryAndVerifyPurchases(showResult: Boolean) {
        val requestGeneration = ++purchaseGeneration
        if (showResult) {
            _state.value = _state.value.copy(purchaseStatus = PremiumPurchaseStatus.Launching)
        }
        withConnectedClient { connectionError ->
            if (requestGeneration != purchaseGeneration) return@withConnectedClient
            if (connectionError != null) {
                premiumEntitlement.setPremiumOwned(false)
                if (showResult) failPurchase()
                return@withConnectedClient
            }
            val purchases = mutableListOf<Purchase>()
            var remaining = 2
            var failed = false
            fun completeOne(result: BillingResult, resultPurchases: List<Purchase>) {
                if (requestGeneration != purchaseGeneration) return
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases += resultPurchases
                } else {
                    failed = true
                }
                remaining--
                if (remaining == 0) {
                    if (failed) {
                        premiumEntitlement.setPremiumOwned(false)
                        if (showResult) failPurchase()
                    } else {
                        verifyPurchases(purchases, showResult, requestGeneration)
                    }
                }
            }
            queryPurchases(BillingClient.ProductType.SUBS, ::completeOne)
            queryPurchases(BillingClient.ProductType.INAPP, ::completeOne)
        }
    }

    private fun queryPurchases(
        type: String,
        onComplete: (BillingResult, List<Purchase>) -> Unit,
    ) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(type).build(),
            onComplete,
        )
    }

    private fun verifyPurchases(
        purchases: List<Purchase>,
        showResult: Boolean,
        requestGeneration: Long = ++purchaseGeneration,
    ) {
        val pending = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PENDING }
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            premiumEntitlement.setPremiumOwned(false)
            _state.value = _state.value.copy(
                purchaseStatus = if (pending != null) {
                    PremiumPurchaseStatus.WaitingForPayment(
                        pending.products.firstOrNull().orEmpty()
                    )
                } else {
                    PremiumPurchaseStatus.Idle
                },
            )
            return
        }
        scope.launch {
            var verifiedPurchase: Purchase? = null
            var verifiedProductId: String? = null
            var hasPendingVerification = false
            purchased.forEach { purchase ->
                purchase.products.forEach productLoop@{ productId ->
                    val type = catalog.typeOf(productId) ?: return@productLoop
                    when (
                        verifier.verify(
                            PurchaseVerificationRequest(
                                productId = productId,
                                productType = type,
                                purchaseToken = purchase.purchaseToken,
                                packageName = context.packageName,
                            ),
                        )
                    ) {
                        PurchaseVerificationResult.Verified -> if (verifiedPurchase == null) {
                            verifiedPurchase = purchase
                            verifiedProductId = productId
                        }

                        PurchaseVerificationResult.Pending,
                        PurchaseVerificationResult.Unavailable,
                            -> hasPendingVerification = true

                        PurchaseVerificationResult.Rejected -> Unit
                    }
                }
            }
            if (requestGeneration != purchaseGeneration) return@launch
            if (verifiedPurchase != null && verifiedProductId != null) {
                premiumEntitlement.setPremiumOwned(true)
                _state.value = _state.value.copy(
                    purchaseStatus = PremiumPurchaseStatus.Verified(requireNotNull(verifiedProductId)),
                )
                acknowledgeIfRequired(requireNotNull(verifiedPurchase))
            } else {
                premiumEntitlement.setPremiumOwned(false)
                _state.value = _state.value.copy(
                    purchaseStatus = if (hasPendingVerification) {
                        PremiumPurchaseStatus.WaitingForVerification(
                            purchased.first().products.firstOrNull().orEmpty(),
                        )
                    } else if (showResult) {
                        PremiumPurchaseStatus.Error
                    } else {
                        PremiumPurchaseStatus.Idle
                    },
                )
            }
        }
    }

    private fun acknowledgeIfRequired(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { /* A later app resume retries verification and acknowledgement if this fails. */ }
    }

    private fun withConnectedClient(onReady: (BillingResult?) -> Unit) {
        if (billingClient.isReady) {
            onReady(null)
            return
        }
        connectionCallbacks += onReady
        if (connecting) return
        connecting = true
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MILLIS)
            if (!connecting) return@launch
            connecting = false
            Log.w(TAG, "Billing connection timed out; resolving entitlement as unavailable.")
            completeConnectionCallbacks(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
                    .setDebugMessage("Billing connection timed out")
                    .build(),
            )
        }
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    connecting = false
                    connectionTimeoutJob?.cancel()
                    connectionTimeoutJob = null
                    completeConnectionCallbacks(
                        BillingResult.newBuilder()
                            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                            .setDebugMessage("Billing service disconnected")
                            .build(),
                    )
                }

                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    connectionTimeoutJob?.cancel()
                    connectionTimeoutJob = null
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(
                            TAG,
                            "Billing setup failed response=${result.responseCode} " +
                                    "message=${result.debugMessage} package=${context.packageName}",
                        )
                    }
                    val error = result.takeUnless {
                        it.responseCode == BillingClient.BillingResponseCode.OK
                    }
                    completeConnectionCallbacks(error)
                }
            },
        )
    }

    private fun completeConnectionCallbacks(error: BillingResult?) {
        val callbacks = connectionCallbacks.toList()
        connectionCallbacks.clear()
        callbacks.forEach { it(error) }
    }

    private fun failPurchase() {
        premiumEntitlement.setPremiumOwned(false)
        _state.value = _state.value.copy(purchaseStatus = PremiumPurchaseStatus.Error)
    }

    private data class ProductQueryResult(
        val success: Boolean,
        val details: List<ProductDetails> = emptyList(),
        val unfetchedIds: Set<String> = emptySet(),
    )

    private fun Int.toUnfetchedReason(): String = when (this) {
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "INVALID_PRODUCT_ID_FORMAT"
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "PRODUCT_NOT_FOUND"
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "NO_ELIGIBLE_OFFER"
        else -> "UNKNOWN($this)"
    }

    private companion object {
        const val TAG = "PremiumBilling"
        const val CONNECTION_TIMEOUT_MILLIS = 8_000L
    }
}
