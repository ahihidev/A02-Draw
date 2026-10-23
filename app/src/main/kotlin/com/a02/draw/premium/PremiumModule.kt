package com.a02.draw.premium

import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.core.ui.ads.RewardUnlockStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PremiumModule {
    @Provides
    @Singleton
    fun providePremiumAccessProvider(manager: PremiumEntitlementManager): PremiumAccessProvider =
        manager

    @Provides
    fun providePremiumEntitlementController(
        manager: PremiumEntitlementManager,
    ): PremiumEntitlementController = manager

    @Provides
    fun provideRewardUnlockStore(manager: RewardUnlockManager): RewardUnlockStore = manager

    @Provides
    @Singleton
    fun provideRewardTimeSource(): RewardTimeSource = SystemRewardTimeSource()
}
