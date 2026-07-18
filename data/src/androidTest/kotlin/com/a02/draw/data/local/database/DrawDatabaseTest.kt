package com.a02.draw.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.a02.draw.data.local.database.entity.DrawingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawDatabaseTest {
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
}
