package com.a02.draw.onboarding.common.di

import com.a02.draw.onboarding.common.session.DefaultOnboardingSessionStore
import com.a02.draw.onboarding.common.session.OnboardingSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class OnboardingSessionModule {
    @Binds
    abstract fun bindOnboardingSessionStore(
        implementation: DefaultOnboardingSessionStore,
    ): OnboardingSessionStore
}
