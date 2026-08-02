package com.a02.draw.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "lessons")
data class LessonEntity(
    @androidx.room.PrimaryKey
    val lessonId: String,
    val name: String,
    val categorySlug: String,
    val categoryName: String,
    val subcategorySlug: String,
    val totalSteps: Int,
    val coverImageUrl: String,
)

@Entity(
    tableName = "lesson_steps",
    primaryKeys = ["lessonId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["lessonId"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lessonId")],
)
data class LessonStepEntity(
    val lessonId: String,
    /** API order is the stable identity because thumbnail rows may duplicate stepNumber. */
    val position: Int,
    val stepNumber: Int,
    val imageUrl: String,
    val storageKey: String,
)
