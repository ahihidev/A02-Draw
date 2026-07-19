package com.a02.draw.data.local.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.a02.draw.data.local.database.dao.DrawingDao
import com.a02.draw.data.local.database.entity.DrawingEntity

@Database(
    entities = [DrawingEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class DrawDatabase : RoomDatabase() {
    abstract fun drawingDao(): DrawingDao

    companion object {
        const val DATABASE_NAME = "a02_draw.db"
    }
}
