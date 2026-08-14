package com.a02.draw.data.repository

import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.data.BuildConfig
import com.a02.draw.data.local.database.dao.LessonDao
import com.a02.draw.data.local.fixture.FixtureArContentDataSource
import com.a02.draw.data.mapper.toAppError
import com.a02.draw.data.mapper.toCacheEntities
import com.a02.draw.data.mapper.toDomain
import com.a02.draw.data.mapper.toRemoteLessons
import com.a02.draw.data.mapper.toRemoteLessonsFromAssets
import com.a02.draw.data.mapper.withRemoteContent
import com.a02.draw.data.mapper.withRemoteLessons
import com.a02.draw.data.remote.api.DrawApi
import com.a02.draw.data.remote.dto.ArCatalogDto
import com.a02.draw.data.remote.dto.RemoteAssetDto
import com.a02.draw.data.remote.dto.RemoteLessonDto
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.repository.ArContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultArContentRepository @Inject constructor(
    private val api: DrawApi,
    private val fixture: FixtureArContentDataSource,
    private val lessonDao: LessonDao,
    private val dispatchers: DispatcherProvider,
) : ArContentRepository {
    private var memoryCache: ArCatalog? = null
    private val catalogMutex = Mutex()

    override suspend fun getCatalog(forceRefresh: Boolean): AppResult<ArCatalog> =
        withContext(dispatchers.io) {
            catalogMutex.withLock {
                if (!forceRefresh) memoryCache?.let { return@withLock AppResult.Success(it) }
                try {
                    val fixtureCatalog = fixture.catalog()
                    val dto = loadRemoteContent(fixtureCatalog)
                        .withRemoteLessons(loadLessons())
                    AppResult.Success(dto.toDomain().also { memoryCache = it })
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    AppResult.Failure(throwable.toAppError())
                }
            }
        }

    private suspend fun loadRemoteContent(fixtureCatalog: ArCatalogDto) =
        if (remoteIsConfigured()) {
            try {
                val categories = api.getCategories().data
                val assets = loadAllAssets()
                fixtureCatalog.withRemoteContent(categories, assets)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                fixtureCatalog
            }
        } else {
            fixtureCatalog
        }

    private suspend fun loadLessons(): List<RemoteLessonDto> {
        if (!remoteIsConfigured()) return loadCachedLessonsOrEmpty()
        return try {
            val lessons = loadAllLessons()
            val cache = lessons.toCacheEntities()
            lessonDao.replaceAll(cache.lessons, cache.steps)
            lessons
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            loadCachedLessonsOrEmpty()
        }
    }

    private suspend fun loadAllAssets(): List<RemoteAssetDto> {
        val firstPage = api.getAssets(page = 1, limit = COMPLETE_CATALOG_LIMIT).data
        val assets = buildList {
            addAll(firstPage.items)
            for (pageNumber in 2..firstPage.totalPages.coerceAtLeast(1)) {
                val page = api.getAssets(page = pageNumber, limit = COMPLETE_CATALOG_LIMIT).data
                check(page.page == pageNumber) { "Asset API returned the wrong page" }
                addAll(page.items)
            }
        }.distinctBy(RemoteAssetDto::assetId)
        check(firstPage.total <= 0 || assets.size == firstPage.total) {
            "Refusing to use an incomplete asset snapshot"
        }
        return assets
    }

    private suspend fun loadAllLessons(): List<RemoteLessonDto> {
        val firstPage = api.getAssets(
            rootFamily = LESSON_ROOT_FAMILY,
            page = 1,
            limit = LESSON_PAGE_SIZE,
        ).data
        val lessonAssets = buildList {
            addAll(firstPage.items)
            for (pageNumber in 2..firstPage.totalPages.coerceAtLeast(1)) {
                val page = api.getAssets(
                    rootFamily = LESSON_ROOT_FAMILY,
                    page = pageNumber,
                    limit = LESSON_PAGE_SIZE,
                ).data
                check(page.page == pageNumber) { "Lesson asset API returned the wrong page" }
                addAll(page.items)
            }
        }.distinctBy(RemoteAssetDto::assetId)
        check(firstPage.total <= 0 || lessonAssets.size == firstPage.total) {
            "Refusing to build lessons from an incomplete asset snapshot"
        }
        return lessonAssets.toRemoteLessonsFromAssets()
    }

    private suspend fun loadCachedLessonsOrEmpty(): List<RemoteLessonDto> = try {
        lessonDao.getAll().toRemoteLessons()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        emptyList()
    }

    private fun remoteIsConfigured(): Boolean =
        BuildConfig.API_BASE_URL.startsWith("http", ignoreCase = true) &&
                BuildConfig.API_AES_KEY.isNotBlank() &&
                BuildConfig.API_AES_IV.isNotBlank()

    private companion object {
        // The current UI owns local filtering and scrolling, so one complete snapshot keeps
        // search/category filters complete without issuing a request for every keystroke.
        const val COMPLETE_CATALOG_LIMIT = 5_000
        const val LESSON_PAGE_SIZE = 20
        const val LESSON_ROOT_FAMILY = "lesson"
    }
}
