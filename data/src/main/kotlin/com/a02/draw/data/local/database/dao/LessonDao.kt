package com.a02.draw.data.local.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.a02.draw.data.local.database.entity.LessonEntity
import com.a02.draw.data.local.database.entity.LessonStepEntity

data class LessonWithSteps(
    @Embedded val lesson: LessonEntity,
    @Relation(
        parentColumn = "lessonId",
        entityColumn = "lessonId",
    )
    val steps: List<LessonStepEntity>,
)

@Dao
interface LessonDao {
    @Transaction
    @Query("SELECT * FROM lessons ORDER BY categorySlug, lessonId")
    suspend fun getAll(): List<LessonWithSteps>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<LessonStepEntity>)

    @Query("DELETE FROM lesson_steps")
    suspend fun deleteSteps()

    @Query("DELETE FROM lessons")
    suspend fun deleteLessons()

    @Transaction
    suspend fun replaceAll(
        lessons: List<LessonEntity>,
        steps: List<LessonStepEntity>,
    ) {
        deleteSteps()
        deleteLessons()
        insertLessons(lessons)
        insertSteps(steps)
    }
}
