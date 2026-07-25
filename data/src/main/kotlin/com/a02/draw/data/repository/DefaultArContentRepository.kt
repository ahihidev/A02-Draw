package com.a02.draw.data.repository

import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.data.BuildConfig
import com.a02.draw.data.local.fixture.FixtureArContentDataSource
import com.a02.draw.data.mapper.toDomain
import com.a02.draw.data.remote.api.DrawApi
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.repository.ArContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultArContentRepository @Inject constructor(
    private val api: DrawApi,
    private val fixture: FixtureArContentDataSource,
    private val dispatchers: DispatcherProvider,
) : ArContentRepository {
    private var memoryCache: ArCatalog? = null

    override suspend fun getCatalog(forceRefresh: Boolean): AppResult<ArCatalog> =
        withContext(dispatchers.io) {
            if (!forceRefresh) memoryCache?.let { return@withContext AppResult.Success(it) }
            val dto = if (remoteIsConfigured()) {
                try {
                    api.getCatalog()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    fixture.catalog()
                }
            } else {
                fixture.catalog()
            }
            AppResult.Success(dto.toDomain().also { memoryCache = it })
        }

    private fun remoteIsConfigured(): Boolean =
        !BuildConfig.API_BASE_URL.contains("example.com", ignoreCase = true)
}
