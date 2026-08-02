package com.a02.draw.data.local.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.a02.draw.data.local.database.dao.DrawingDao
import com.a02.draw.data.local.database.dao.LessonDao
import com.a02.draw.data.local.database.entity.DrawingEntity
import com.a02.draw.data.local.database.entity.LessonEntity
import com.a02.draw.data.local.database.entity.LessonStepEntity

@Database(
    entities = [DrawingEntity::class, LessonEntity::class, LessonStepEntity::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
    exportSchema = true,
)
abstract class DrawDatabase : RoomDatabase() {
    abstract fun drawingDao(): DrawingDao
    abstract fun lessonDao(): LessonDao

    companion object {
        const val DATABASE_NAME = "a02_draw.db"
    }
}
