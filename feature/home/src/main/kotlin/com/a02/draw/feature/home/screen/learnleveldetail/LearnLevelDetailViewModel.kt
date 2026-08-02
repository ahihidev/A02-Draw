package com.a02.draw.feature.home.screen.learnleveldetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class LearnLevelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCatalog: GetArCatalogUseCase,
    observePreferences: ObserveAppPreferencesUseCase,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<LearnLevelDetailUiState, LearnLevelDetailEffect>(LearnLevelDetailUiState()) {
    private val lessonId = savedStateHandle.get<String>("lessonId").orEmpty()
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            observePreferences().collect { preferences ->
                updateState {
                    copy(
                        completedSteps = preferences.lessonCompletedSteps[lessonId] ?: 0
                    )
                }
            }
        }
    }

    fun onAction(action: LearnLevelDetailAction) {
        when (action) {
            LearnLevelDetailAction.Back -> send(LearnLevelDetailEffect.NavigateBack)
            LearnLevelDetailAction.Retry -> load(true)
            LearnLevelDetailAction.OpenTutorial -> openTutorial()
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> updateState {
                    copy(
                        lesson = result.data.lessons.firstOrNull { it.id == lessonId },
                        isLoading = false,
                        hasError = false,
                    )
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun openTutorial() {
        val lesson = state.value.lesson ?: return
        val steps = lesson.steps.sortedBy { it.stepNumber }.map { it.image }
        val completed = state.value.completedSteps
        val startIndex = completed.takeIf { it in 1 until steps.size } ?: 0
        drawingSession.reset(
            DrawingSession(
                lessonId = lesson.id,
                categoryId = lesson.categoryId,
                referenceTitle = lesson.title,
                referenceImage = lesson.image,
                traceImage = steps.getOrNull(startIndex) ?: lesson.image,
                lessonSteps = steps,
                lessonMinutes = lesson.minutes,
                lessonStepIndex = startIndex,
                lessonStepCount = lesson.steps.size.takeIf { it > 0 } ?: lesson.totalSteps,
                mode = DrawingMode.CAMERA,
            ),
        )
        send(LearnLevelDetailEffect.NavigateTutorial)
    }

    private fun send(effect: LearnLevelDetailEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
