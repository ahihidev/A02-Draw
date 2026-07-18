package com.a02.draw.data.di

import com.a02.draw.core.common.coroutines.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        implementation: DefaultDispatcherProvider,
    ): DispatcherProvider
}
