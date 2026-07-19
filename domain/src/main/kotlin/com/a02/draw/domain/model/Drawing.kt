package com.a02.draw.domain.model

data class Drawing(
    val id: Long = 0,
    val title: String,
    val updatedAtEpochMillis: Long,
    val mediaUri: String? = null,
    val artworkId: String? = null,
)
