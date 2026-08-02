package com.a02.draw.feature.home.common.session

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ActivityRetainedScoped
class DefaultDrawingSessionStore @Inject constructor() : DrawingSessionStore {
    private val mutableState = MutableStateFlow(DrawingSession())
    override val state: StateFlow<DrawingSession> = mutableState.asStateFlow()

    override fun update(reducer: DrawingSession.() -> DrawingSession) {
        mutableState.value = mutableState.value.reducer()
    }

    override fun reset(reference: DrawingSession) {
        mutableState.value = reference
    }
}
