package com.a02.draw.feature.home.common.di

import com.a02.draw.feature.home.common.session.DefaultDrawingSessionStore
import com.a02.draw.feature.home.common.session.DefaultGalleryFilterSessionStore
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import com.a02.draw.feature.home.common.session.GalleryFilterSessionStore
import com.a02.draw.feature.home.common.image.DefaultReferenceImageStore
import com.a02.draw.feature.home.common.image.ReferenceImageStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class FeatureSessionModule {
    @Binds
    abstract fun bindDrawingSessionStore(
        implementation: DefaultDrawingSessionStore,
    ): DrawingSessionStore

    @Binds
    abstract fun bindGalleryFilterSessionStore(
        implementation: DefaultGalleryFilterSessionStore,
    ): GalleryFilterSessionStore

    @Binds
    abstract fun bindReferenceImageStore(
        implementation: DefaultReferenceImageStore,
    ): ReferenceImageStore
}
