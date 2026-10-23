package com.a02.draw.billing

import com.a02.draw.core.ui.billing.PremiumProductType
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PremiumProductDetailsMapperTest {
    private val catalog = mockk<BillingProductCatalog> {
        every { productOrder(any(), any()) } returns 0
    }

    @Test
    fun `creates one card per base plan and prefers the longest free trial`() {
        val monthlyShortTrial = subscriptionOffer(
            basePlan = "monthly",
            offerIdentifier = "trial-7",
            token = "short",
            phases = listOf(
                pricing("Free", 0L, "P7D", ProductDetails.RecurrenceMode.FINITE_RECURRING),
                pricing(
                    "€4.99",
                    4_990_000L,
                    "P1M",
                    ProductDetails.RecurrenceMode.INFINITE_RECURRING
                ),
            ),
        )
        val monthlyLongTrial = subscriptionOffer(
            basePlan = "monthly",
            offerIdentifier = "trial-14",
            token = "long",
            phases = listOf(
                pricing("Free", 0L, "P14D", ProductDetails.RecurrenceMode.FINITE_RECURRING),
                pricing(
                    "€4.99",
                    4_990_000L,
                    "P1M",
                    ProductDetails.RecurrenceMode.INFINITE_RECURRING
                ),
            ),
        )
        val yearly = subscriptionOffer(
            basePlan = "yearly",
            offerIdentifier = null,
            token = "yearly",
            phases = listOf(
                pricing(
                    "€29.99",
                    29_990_000L,
                    "P1Y",
                    ProductDetails.RecurrenceMode.INFINITE_RECURRING
                ),
            ),
        )

        val result = PremiumProductDetailsMapper.map(
            listOf(subscriptionProduct(listOf(monthlyShortTrial, monthlyLongTrial, yearly))),
            catalog,
        )

        assertEquals(2, result.size)
        assertEquals("long", result.first { it.offer.basePlanId == "monthly" }.offerToken)
        assertEquals(
            "€4.99",
            result.first { it.offer.basePlanId == "monthly" }.offer.primaryFormattedPrice
        )
        assertEquals("Free", result.first().offer.pricingPhases.first().formattedPrice)
    }

    @Test
    fun `preserves eligible three day free trial before yearly recurring phase`() {
        val yearlyTrial = subscriptionOffer(
            basePlan = "yearly",
            offerIdentifier = "trial-3-days",
            token = "yearly-trial",
            phases = listOf(
                pricing("Free", 0L, "P3D", ProductDetails.RecurrenceMode.FINITE_RECURRING),
                pricing(
                    "₫600,000",
                    600_000_000_000L,
                    "P1Y",
                    ProductDetails.RecurrenceMode.INFINITE_RECURRING
                ),
            ),
        )

        val result = PremiumProductDetailsMapper.map(
            listOf(subscriptionProduct(listOf(yearlyTrial))),
            catalog,
        ).single()

        assertEquals("yearly-trial", result.offerToken)
        assertEquals("P3D", result.offer.pricingPhases.first().billingPeriod)
        assertEquals(true, result.offer.pricingPhases.first().isFree)
        assertEquals("₫600,000", result.offer.primaryFormattedPrice)
    }

    @Test
    fun `lifetime selects the cheapest Play offer and preserves formatted price`() {
        val expensive = oneTimeOffer("lifetime-full", "¥9,800", 9_800_000L)
        val cheap = oneTimeOffer("lifetime-sale", "¥6,800", 6_800_000L)
        val product = mockk<ProductDetails> {
            every { productId } returns "premium_lifetime"
            every { productType } returns BillingClient.ProductType.INAPP
            every { name } returns "Lifetime"
            every { description } returns "One payment"
            every { oneTimePurchaseOfferDetailsList } returns listOf(expensive, cheap)
            every { oneTimePurchaseOfferDetails } returns cheap
        }

        val result = PremiumProductDetailsMapper.map(listOf(product), catalog).single()

        assertEquals(PremiumProductType.LIFETIME, result.offer.productType)
        assertEquals("¥6,800", result.offer.primaryFormattedPrice)
        assertEquals("lifetime-sale", result.offerToken)
        assertFalse(result.offer.pricingPhases.single().isFree)
    }

    @Test
    fun `period scoring orders days weeks months and years`() {
        assertEquals(14L, PremiumProductDetailsMapper.billingPeriodWeight("P2W"))
        assertEquals(30L, PremiumProductDetailsMapper.billingPeriodWeight("P1M"))
        assertEquals(365L, PremiumProductDetailsMapper.billingPeriodWeight("P1Y"))
    }

    private fun subscriptionProduct(
        offers: List<ProductDetails.SubscriptionOfferDetails>,
    ) = mockk<ProductDetails> {
        every { productId } returns "premium_subscription"
        every { productType } returns BillingClient.ProductType.SUBS
        every { name } returns "Premium"
        every { description } returns "Premium subscription"
        every { subscriptionOfferDetails } returns offers
    }

    private fun subscriptionOffer(
        basePlan: String,
        offerIdentifier: String?,
        token: String,
        phases: List<ProductDetails.PricingPhase>,
    ) = mockk<ProductDetails.SubscriptionOfferDetails> {
        every { basePlanId } returns basePlan
        every { offerId } returns offerIdentifier
        every { offerToken } returns token
        every { pricingPhases } returns mockk {
            every { pricingPhaseList } returns phases
        }
    }

    private fun pricing(
        formatted: String,
        micros: Long,
        period: String,
        recurrence: Int,
    ) = mockk<ProductDetails.PricingPhase> {
        every { formattedPrice } returns formatted
        every { priceAmountMicros } returns micros
        every { billingPeriod } returns period
        every { billingCycleCount } returns 1
        every { recurrenceMode } returns recurrence
    }

    private fun oneTimeOffer(
        token: String,
        formatted: String,
        micros: Long,
    ) = mockk<ProductDetails.OneTimePurchaseOfferDetails> {
        every { offerToken } returns token
        every { formattedPrice } returns formatted
        every { priceAmountMicros } returns micros
    }
}
