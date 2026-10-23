package com.a02.draw.billing

import android.content.Context
import com.a02.draw.R
import com.a02.draw.core.ui.billing.PremiumProductType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingProductCatalog @Inject constructor(
    @ApplicationContext context: Context,
) {
    val subscriptionIds: List<String> =
        context.resources.getStringArray(R.array.billing_subscription_product_ids).sanitize()
    val inAppIds: List<String> =
        context.resources.getStringArray(R.array.billing_inapp_product_ids).sanitize()

    val allIds: Set<String> = (subscriptionIds + inAppIds).toSet()

    fun typeOf(productId: String): PremiumProductType? = when (productId) {
        in subscriptionIds -> PremiumProductType.SUBSCRIPTION
        in inAppIds -> PremiumProductType.LIFETIME
        else -> null
    }

    fun productOrder(type: PremiumProductType, productId: String): Int {
        val ids = if (type == PremiumProductType.SUBSCRIPTION) subscriptionIds else inAppIds
        return ids.indexOf(productId).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}

private fun Array<String>.sanitize(): List<String> =
    map(String::trim).filter(String::isNotEmpty).distinct()
