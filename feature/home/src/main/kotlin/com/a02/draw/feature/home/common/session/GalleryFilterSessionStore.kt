package com.a02.draw.feature.home.common.session

import com.a02.draw.domain.model.ArtworkStyle
import com.a02.draw.feature.home.common.model.GalleryFilter
import kotlinx.coroutines.flow.StateFlow

data class GalleryFilterSession(
    val topicId: String? = null,
    val difficulty: String? = null,
    val style: ArtworkStyle? = null,
    val quickFilter: GalleryFilter = GalleryFilter.ALL,
)

interface GalleryFilterSessionStore {
    val state: StateFlow<GalleryFilterSession>
    fun update(reducer: GalleryFilterSession.() -> GalleryFilterSession)
    fun clear()
}
