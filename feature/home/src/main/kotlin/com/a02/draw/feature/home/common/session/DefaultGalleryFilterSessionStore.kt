package com.a02.draw.feature.home.common.session

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ActivityRetainedScoped
class DefaultGalleryFilterSessionStore @Inject constructor() : GalleryFilterSessionStore {
    private val mutableState = MutableStateFlow(GalleryFilterSession())
    override val state: StateFlow<GalleryFilterSession> = mutableState.asStateFlow()

    override fun update(reducer: GalleryFilterSession.() -> GalleryFilterSession) {
        mutableState.value = mutableState.value.reducer()
    }

    override fun clear() {
        mutableState.value = GalleryFilterSession(topicId = mutableState.value.topicId)
    }
}
