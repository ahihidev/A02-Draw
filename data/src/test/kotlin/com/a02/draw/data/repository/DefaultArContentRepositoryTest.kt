package com.a02.draw.data.repository

import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.data.local.database.dao.LessonDao
import com.a02.draw.data.local.database.dao.LessonWithSteps
import com.a02.draw.data.local.database.entity.LessonEntity
import com.a02.draw.data.local.database.entity.LessonStepEntity
import com.a02.draw.data.local.fixture.FixtureArContentDataSource
import com.a02.draw.data.remote.api.DrawApi
import com.a02.draw.data.remote.dto.DrawingDto
import com.a02.draw.data.remote.dto.RemoteAssetDto
import com.a02.draw.data.remote.dto.RemoteAssetEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteAssetPageDto
import com.a02.draw.data.remote.dto.RemoteAssetsEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteCategoriesEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteCategoryDto
import com.a02.draw.data.remote.dto.RemoteLessonDto
import com.a02.draw.data.remote.dto.RemoteLessonPageDto
import com.a02.draw.data.remote.dto.RemoteLessonsEnvelopeDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultArContentRepositoryTest {
    @Test
    fun `loads every API page once for concurrent catalog requests`() = runTest {
        val api = FakeDrawApi()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = DefaultArContentRepository(
            api = api,
            fixture = FixtureArContentDataSource(),
            lessonDao = FakeLessonDao(),
            dispatchers = object : DispatcherProvider {
                override val io = dispatcher
                override val default = dispatcher
                override val main = dispatcher
            },
        )

        val results = (1..3).map { async { repository.getCatalog() } }.awaitAll()

        assertTrue(results.all { it is AppResult.Success })
        val catalog = (results.first() as AppResult.Success).data
        assertEquals(setOf("asset-1", "asset-2"), catalog.artworks.map { it.id }.toSet())
        assertEquals(setOf("lesson-1", "lesson-2"), catalog.lessons.map { it.id }.toSet())
        assertEquals(mapOf(1 to 1, 2 to 1), api.assetPageCalls)
        assertEquals(mapOf(1 to 1, 2 to 1), api.lessonPageCalls)
    }

    private class FakeDrawApi : DrawApi {
        val assetPageCalls = mutableMapOf<Int, Int>()
        val lessonPageCalls = mutableMapOf<Int, Int>()

        override suspend fun getCategories() = RemoteCategoriesEnvelopeDto(
            listOf(RemoteCategoryDto("animal", "Animal", 2)),
        )

        override suspend fun getAssets(
            category: String?,
            subcategory: String?,
            search: String?,
            page: Int,
            limit: Int,
        ): RemoteAssetsEnvelopeDto {
            assetPageCalls[page] = assetPageCalls.getOrDefault(page, 0) + 1
            return RemoteAssetsEnvelopeDto(
                RemoteAssetPageDto(
                    items = listOf(asset(page)),
                    page = page,
                    limit = limit,
                    total = 2,
                    totalPages = 2,
                ),
            )
        }

        override suspend fun getLessons(
            category: String?,
            page: Int,
            limit: Int,
        ): RemoteLessonsEnvelopeDto {
            lessonPageCalls[page] = lessonPageCalls.getOrDefault(page, 0) + 1
            return RemoteLessonsEnvelopeDto(
                RemoteLessonPageDto(
                    items = listOf(lesson(page)),
                    page = page,
                    limit = limit,
                    total = 2,
                    totalPages = 2,
                ),
            )
        }

        override suspend fun getAsset(id: String) = RemoteAssetEnvelopeDto(asset(1))

        override suspend fun getDrawings(): List<DrawingDto> = emptyList()

        private fun asset(index: Int) = RemoteAssetDto(
            assetId = "asset-$index",
            name = "Asset $index",
            categorySlug = "animal",
            categoryName = "Animal",
            imageUrl = "https://example.com/asset-$index.png",
        )

        private fun lesson(index: Int) = RemoteLessonDto(
            lessonId = "lesson-$index",
            name = "Lesson $index",
            categorySlug = "animal",
            categoryName = "Animal",
            subcategorySlug = "beginner",
            totalSteps = 1,
            coverImageUrl = "https://example.com/lesson-$index.png",
        )
    }

    private class FakeLessonDao : LessonDao {
        override suspend fun getAll(): List<LessonWithSteps> = emptyList()
        override suspend fun insertLessons(lessons: List<LessonEntity>) = Unit
        override suspend fun insertSteps(steps: List<LessonStepEntity>) = Unit
        override suspend fun deleteSteps() = Unit
        override suspend fun deleteLessons() = Unit
    }
}
