package com.a02.draw.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.a02.draw.data.local.database.entity.DrawingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawings ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DrawingEntity>>

    @Query("SELECT * FROM drawings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DrawingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: DrawingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<DrawingEntity>)

    @Query("DELETE FROM drawings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM drawings")
    suspend fun count(): Int
}
