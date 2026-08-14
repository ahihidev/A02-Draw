package com.a02.draw.data.local.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.a02.draw.data.local.database.entity.DrawingEntity
import com.a02.draw.data.local.database.entity.LessonEntity
import com.a02.draw.data.local.database.entity.LessonStepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DrawDatabase::class.java,
    )

    private lateinit var database: DrawDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DrawDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertedDrawingIsObserved() = runTest {
        database.drawingDao().save(
            DrawingEntity(title = "Database sketch", updatedAtEpochMillis = 100),
        )

        val drawings = database.drawingDao().observeAll().first()

        assertEquals("Database sketch", drawings.single().title)
    }

    @Test
    fun versionThreeMigratesWithoutLosingDrawingsAndDefaultsToCameraMode() {
        migrationHelper.createDatabase(MIGRATION_DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO drawings
                    (id, title, updatedAtEpochMillis, mediaUri, artworkId, lessonId, lessonMinutes)
                VALUES
                    (7, 'Legacy canvas', 100, 'content://legacy.jpg', NULL, 'lesson-1', 25)
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            4,
            true,
        )
        migrated.query(
            "SELECT id, title, lessonId, lessonMinutes, usesCamera FROM drawings WHERE id = 7"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(7, cursor.getLong(0))
            assertEquals("Legacy canvas", cursor.getString(1))
            assertEquals("lesson-1", cursor.getString(2))
            assertEquals(25, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
        }
    }

    @Test
    fun lessonSnapshotPreservesApiOrderAndDuplicateStepNumbers() = runTest {
        val lesson = LessonEntity(
            lessonId = "animal-10",
            name = "Animal 10",
            categorySlug = "animal",
            categoryName = "Animal",
            subcategorySlug = "animal-10",
            totalSteps = 3,
            coverImageUrl = "https://example.test/cover.webp",
        )
        database.lessonDao().replaceAll(
            lessons = listOf(lesson),
            steps = listOf(
                LessonStepEntity("animal-10", 0, 1, "https://example.test/thumb.webp", "thumb"),
                LessonStepEntity("animal-10", 1, 1, "https://example.test/1.webp", "step-1"),
                LessonStepEntity("animal-10", 2, 2, "https://example.test/2.webp", "step-2"),
            ),
        )

        val cached = database.lessonDao().getAll().single()

        assertEquals(lesson, cached.lesson)
        assertEquals(listOf(0, 1, 2), cached.steps.sortedBy { it.position }.map { it.position })
        assertEquals(listOf(1, 1, 2), cached.steps.sortedBy { it.position }.map { it.stepNumber })
    }

    @Test
    fun versionFourMigratesWithoutLosingDrawingsAndCreatesLessonCache() {
        migrationHelper.createDatabase(MIGRATION_FOUR_DATABASE_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO drawings
                    (id, title, updatedAtEpochMillis, mediaUri, artworkId, lessonId, lessonMinutes, usesCamera)
                VALUES
                    (8, 'Existing drawing', 101, NULL, NULL, 'animal-1', NULL, 0)
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            MIGRATION_FOUR_DATABASE_NAME,
            5,
            true,
        )
        migrated.query("SELECT title, lessonId, usesCamera FROM drawings WHERE id = 8")
            .use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Existing drawing", cursor.getString(0))
                assertEquals("animal-1", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        migrated.query("SELECT COUNT(*) FROM lessons").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val MIGRATION_DATABASE_NAME = "migration-3-to-4.db"
        const val MIGRATION_FOUR_DATABASE_NAME = "migration-4-to-5.db"
    }
}
