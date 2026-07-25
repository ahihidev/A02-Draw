package com.a02.draw.data.local.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.a02.draw.data.local.database.entity.DrawingEntity
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

    private companion object {
        const val MIGRATION_DATABASE_NAME = "migration-3-to-4.db"
    }
}
