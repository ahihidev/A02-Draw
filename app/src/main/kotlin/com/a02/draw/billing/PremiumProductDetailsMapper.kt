package com.a02.draw.billing

import com.a02.draw.core.ui.billing.PremiumOffer
import com.a02.draw.core.ui.billing.PremiumPricingPhase
import com.a02.draw.core.ui.billing.PremiumProductType
import com.a02.draw.core.ui.billing.PremiumRecurrenceMode
import com.android.billingclient.api.ProductDetails

internal data class BillingOfferHandle(
    val offer: PremiumOffer,
    val productDetails: ProductDetails,
    val offerToken: String?,
)

internal object PremiumProductDetailsMapper {
    fun map(
        details: List<ProductDetails>,
        catalog: BillingProductCatalog,
    ): List<BillingOfferHandle> = details.flatMap { product ->
        when (product.productType) {
            com.android.billingclient.api.BillingClient.ProductType.SUBS ->
                mapSubscriptions(product)

            com.android.billingclient.api.BillingClient.ProductType.INAPP ->
                mapOneTimeProducts(product)

            else -> emptyList()
        }
    }.sortedWith(
        compareBy<BillingOfferHandle> {
            if (it.offer.productType == PremiumProductType.SUBSCRIPTION) 0 else 1
        }.thenBy { catalog.productOrder(it.offer.productType, it.offer.productId) }
            .thenBy { it.offer.basePlanId.orEmpty() }
            .thenBy { it.offer.offerId.orEmpty() },
    )

    private fun mapSubscriptions(product: ProductDetails): List<BillingOfferHandle> =
        product.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .values
            .mapNotNull { basePlanOffers ->
                val selected = basePlanOffers.sortedWith(subscriptionOfferComparator).firstOrNull()
                    ?: return@mapNotNull null
                val phases = selected.pricingPhases.pricingPhaseList.map { phase ->
                    PremiumPricingPhase(
                        formattedPrice = phase.formattedPrice,
                        billingPeriod = phase.billingPeriod,
                        billingCycleCount = phase.billingCycleCount,
                        recurrenceMode = phase.recurrenceMode.toUiRecurrence(),
                        isFree = phase.priceAmountMicros == 0L,
                    )
                }
                val recurring = selected.pricingPhases.pricingPhaseList
                    .lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
                    ?: selected.pricingPhases.pricingPhaseList.lastOrNull()
                    ?: return@mapNotNull null
                BillingOfferHandle(
                    offer = PremiumOffer(
                        key = subscriptionKey(
                            product.productId,
                            selected.basePlanId,
                            selected.offerId
                        ),
                        productId = product.productId,
                        productType = PremiumProductType.SUBSCRIPTION,
                        title = product.name,
                        description = product.description,
                        basePlanId = selected.basePlanId,
                        offerId = selected.offerId,
                        primaryFormattedPrice = recurring.formattedPrice,
                        pricingPhases = phases,
                    ),
                    productDetails = product,
                    offerToken = selected.offerToken,
                )
            }

    private fun mapOneTimeProducts(product: ProductDetails): List<BillingOfferHandle> {
        val selected = product.oneTimePurchaseOfferDetailsList
            ?.minWithOrNull(compareBy<ProductDetails.OneTimePurchaseOfferDetails> { it.priceAmountMicros }
                .thenBy { it.offerToken })
            ?: product.oneTimePurchaseOfferDetails
            ?: return emptyList()
        return listOf(
            BillingOfferHandle(
                offer = PremiumOffer(
                    key = lifetimeKey(product.productId, selected.offerToken),
                    productId = product.productId,
                    productType = PremiumProductType.LIFETIME,
                    title = product.name,
                    description = product.description,
                    primaryFormattedPrice = selected.formattedPrice,
                    pricingPhases = listOf(
                        PremiumPricingPhase(
                            formattedPrice = selected.formattedPrice,
                            billingPeriod = null,
                            billingCycleCount = 1,
                            recurrenceMode = PremiumRecurrenceMode.ONE_TIME,
                            isFree = false,
                        ),
                    ),
                ),
                productDetails = product,
                offerToken = selected.offerToken,
            ),
        )
    }

    private val subscriptionOfferComparator =
        compareByDescending<ProductDetails.SubscriptionOfferDetails> { offer ->
            offer.pricingPhases.pricingPhaseList
                .filter { it.priceAmountMicros == 0L }
                .maxOfOrNull { billingPeriodWeight(it.billingPeriod) } ?: 0L
        }.thenByDescending { offer ->
            offer.pricingPhases.pricingPhaseList.any { phase ->
                phase.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING ||
                        offer.pricingPhases.pricingPhaseList.size > 1
            }
        }.thenBy { offer ->
            val hasIntroductoryPhase = offer.pricingPhases.pricingPhaseList.any { phase ->
                phase.recurrenceMode == ProductDetails.RecurrenceMode.FINITE_RECURRING
            } || offer.pricingPhases.pricingPhaseList.size > 1
            if (hasIntroductoryPhase) {
                offer.pricingPhases.pricingPhaseList
                    .firstOrNull { it.priceAmountMicros > 0L }
                    ?.priceAmountMicros ?: Long.MAX_VALUE
            } else {
                Long.MAX_VALUE
            }
        }.thenBy { it.offerId != null }
            .thenBy { it.offerId.orEmpty() }

    internal fun billingPeriodWeight(period: String): Long {
        val values = Regex("(\\d+)([YMWD])").findAll(period).sumOf { match ->
            val value = match.groupValues[1].toLongOrNull() ?: 0L
            when (match.groupValues[2]) {
                "Y" -> value * 365L
                "M" -> value * 30L
                "W" -> value * 7L
                else -> value
            }
        }
        return values
    }

    internal fun subscriptionKey(productId: String, basePlanId: String, offerId: String?) =
        "subs:$productId:$basePlanId:${offerId.orEmpty()}"

    internal fun lifetimeKey(productId: String, offerToken: String?) =
        "inapp:$productId:${offerToken.orEmpty()}"
}

private fun Int.toUiRecurrence(): PremiumRecurrenceMode = when (this) {
    ProductDetails.RecurrenceMode.FINITE_RECURRING -> PremiumRecurrenceMode.FINITE
    ProductDetails.RecurrenceMode.INFINITE_RECURRING -> PremiumRecurrenceMode.INFINITE
    else -> PremiumRecurrenceMode.ONE_TIME
}
