package com.a02.draw.data.di

import com.a02.draw.data.local.preferences.AppPreferencesDataStore
import com.a02.draw.data.repository.DefaultArContentRepository
import com.a02.draw.data.repository.DefaultDrawingRepository
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.repository.ArContentRepository
import com.a02.draw.domain.repository.DrawingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindArContentRepository(
        implementation: DefaultArContentRepository,
    ): ArContentRepository

    @Binds
    @Singleton
    abstract fun bindDrawingRepository(
        implementation: DefaultDrawingRepository,
    ): DrawingRepository

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        implementation: AppPreferencesDataStore,
    ): AppPreferencesRepository
}
