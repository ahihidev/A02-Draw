package com.a02.draw.ads.app

import com.a02.draw.core.ui.ads.AppAdsController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppAdsModule {
    @Binds
    abstract fun bindAppAdsController(implementation: AppAdsCoordinator): AppAdsController

    @Binds
    internal abstract fun bindRewardedAdGateway(
        implementation: KiroRewardedAdGateway,
    ): RewardedAdGateway
}
