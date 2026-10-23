package com.a02.draw.billing

import android.content.Context
import com.a02.draw.core.ui.billing.PremiumProductType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingClientPurchaseVerificationGatewayTest {
    private val context = mockk<Context> {
        every { packageName } returns PACKAGE_NAME
    }
    private val gateway = BillingClientPurchaseVerificationGateway(context)

    @Test
    fun `accepts complete purchase metadata for the installed Play package`() = runTest {
        val result = gateway.verify(request())

        assertEquals(PurchaseVerificationResult.Verified, result)
    }

    @Test
    fun `rejects purchase from a different package`() = runTest {
        val result = gateway.verify(request(packageName = "other.package"))

        assertEquals(PurchaseVerificationResult.Rejected, result)
    }

    @Test
    fun `rejects a blank Play purchase token`() = runTest {
        val result = gateway.verify(request(purchaseToken = ""))

        assertEquals(PurchaseVerificationResult.Rejected, result)
    }

    private fun request(
        packageName: String = PACKAGE_NAME,
        purchaseToken: String = "play-purchase-token",
    ) = PurchaseVerificationRequest(
        productId = "com.ledkeyboard.weekly",
        productType = PremiumProductType.SUBSCRIPTION,
        purchaseToken = purchaseToken,
        packageName = packageName,
    )

    private companion object {
        const val PACKAGE_NAME = "com.led.keyboard.neon.classic"
    }
}
