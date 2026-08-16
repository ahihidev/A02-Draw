package com.a02.draw.core.ui.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

enum class PremiumProductType {
    SUBSCRIPTION,
    LIFETIME,
}

enum class PremiumRecurrenceMode {
    ONE_TIME,
    FINITE,
    INFINITE,
}

data class PremiumPricingPhase(
    val formattedPrice: String,
    val billingPeriod: String?,
    val billingCycleCount: Int,
    val recurrenceMode: PremiumRecurrenceMode,
    val isFree: Boolean = false,
)

data class PremiumOffer(
    val key: String,
    val productId: String,
    val productType: PremiumProductType,
    val title: String,
    val description: String,
    val basePlanId: String? = null,
    val offerId: String? = null,
    val primaryFormattedPrice: String,
    val pricingPhases: List<PremiumPricingPhase>,
)

enum class PremiumCatalogStatus {
    IDLE,
    LOADING,
    READY,
    EMPTY,
    ERROR,
}

sealed interface PremiumPurchaseStatus {
    data object Idle : PremiumPurchaseStatus
    data object Launching : PremiumPurchaseStatus
    data class WaitingForPayment(val productId: String) : PremiumPurchaseStatus
    data class WaitingForVerification(val productId: String) : PremiumPurchaseStatus
    data class Verified(val productId: String) : PremiumPurchaseStatus
    data object Canceled : PremiumPurchaseStatus
    data object Error : PremiumPurchaseStatus
}

data class PremiumBillingState(
    val catalogStatus: PremiumCatalogStatus = PremiumCatalogStatus.IDLE,
    val offers: List<PremiumOffer> = emptyList(),
    val unfetchedProductIds: Set<String> = emptySet(),
    val selectedOfferKey: String? = null,
    val purchaseStatus: PremiumPurchaseStatus = PremiumPurchaseStatus.Idle,
)

interface PremiumBillingController {
    val state: StateFlow<PremiumBillingState>

    fun refreshProducts()

    fun selectOffer(offerKey: String)

    fun launchPurchase(activity: Activity, offerKey: String)

    fun restorePurchases()

    fun synchronizeEntitlement()
}
