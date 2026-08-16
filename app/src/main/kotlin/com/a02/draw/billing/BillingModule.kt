package com.a02.draw.billing

import com.a02.draw.core.ui.billing.PremiumBillingController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    abstract fun bindPremiumBillingController(
        implementation: GooglePremiumBillingController,
    ): PremiumBillingController

    @Binds
    @Singleton
    abstract fun bindPurchaseVerificationGateway(
        implementation: UnavailablePurchaseVerificationGateway,
    ): PurchaseVerificationGateway
}
