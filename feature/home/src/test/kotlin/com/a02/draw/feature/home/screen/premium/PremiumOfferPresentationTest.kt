package com.a02.draw.feature.home.screen.premium

import com.a02.draw.core.ui.billing.PremiumOffer
import com.a02.draw.core.ui.billing.PremiumPricingPhase
import com.a02.draw.core.ui.billing.PremiumProductType
import com.a02.draw.core.ui.billing.PremiumRecurrenceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumOfferPresentationTest {
    @Test
    fun `recurring period weight lets yearly plan win the popular badge`() {
        val weekly = offer("weekly", "P1W", "₫105,000")
        val monthly = offer("monthly", "P1M", "₫300,000")
        val yearly = offer("yearly", "P1Y", "₫600,000")

        val popular = listOf(weekly, monthly, yearly).maxBy { it.recurringPeriodWeight() }

        assertEquals("yearly", popular.productId)
        assertEquals("₫600,000", popular.primaryFormattedPrice)
    }

    private fun offer(id: String, period: String, formattedPrice: String) = PremiumOffer(
        key = id,
        productId = id,
        productType = PremiumProductType.SUBSCRIPTION,
        title = id,
        description = id,
        primaryFormattedPrice = formattedPrice,
        pricingPhases = listOf(
            PremiumPricingPhase(
                formattedPrice = formattedPrice,
                billingPeriod = period,
                billingCycleCount = 0,
                recurrenceMode = PremiumRecurrenceMode.INFINITE,
            ),
        ),
    )
}
