package com.a02.draw.billing

import com.a02.draw.core.ui.billing.PremiumProductType
import javax.inject.Inject

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
 * Safe placeholder until the server verification endpoint is configured.
 * Purchases remain unacknowledged and never grant premium in this implementation.
 */
class UnavailablePurchaseVerificationGateway @Inject constructor() : PurchaseVerificationGateway {
    override suspend fun verify(
        request: PurchaseVerificationRequest,
    ): PurchaseVerificationResult = PurchaseVerificationResult.Unavailable
}
