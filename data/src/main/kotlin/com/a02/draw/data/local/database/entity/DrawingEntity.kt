package com.a02.draw.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawings")
data class DrawingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val updatedAtEpochMillis: Long,
    val mediaUri: String? = null,
    val artworkId: String? = null,
)
