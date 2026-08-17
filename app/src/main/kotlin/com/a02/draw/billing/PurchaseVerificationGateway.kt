package com.a02.draw.billing

import android.content.Context
import com.a02.draw.core.ui.billing.PremiumProductType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PurchaseVerificationRequest(
    val productId: String,
    val productType: PremiumProductType,
    val purchaseToken: String,
    val packageName: String,
)

sealed interface PurchaseVerificationResult {
    data object Verified : PurchaseVerificationResult
    data object Pending : PurchaseVerificationResult
    data object Rejected : PurchaseVerificationResult
    data object Unavailable : PurchaseVerificationResult
}

interface PurchaseVerificationGateway {
    suspend fun verify(request: PurchaseVerificationRequest): PurchaseVerificationResult
}

/**
 * Client-only entitlement verification for purchases returned by Google Play BillingClient.
 * A backend using the Google Play Developer API should replace this gateway when available.
 */
@Singleton
class BillingClientPurchaseVerificationGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PurchaseVerificationGateway {
    override suspend fun verify(
        request: PurchaseVerificationRequest,
    ): PurchaseVerificationResult = if (
        request.packageName == context.packageName &&
        request.productId.isNotBlank() &&
        request.purchaseToken.isNotBlank()
    ) {
        PurchaseVerificationResult.Verified
    } else {
        PurchaseVerificationResult.Rejected
    }
}
