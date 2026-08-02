package com.a02.draw.feature.home.screen.learncategorydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.ArCatalog
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
class LearnCategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCatalog: GetArCatalogUseCase,
    observePreferences: ObserveAppPreferencesUseCase,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<LearnCategoryDetailUiState, LearnCategoryDetailEffect>(LearnCategoryDetailUiState()) {
    private val categoryId = savedStateHandle.get<String>("categoryId").orEmpty()
    private var catalog: ArCatalog? = null
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            observePreferences().collect { preferences ->
                updateState { copy(completedSteps = preferences.lessonCompletedSteps) }
            }
        }
    }

    fun onAction(action: LearnCategoryDetailAction) {
        when (action) {
            LearnCategoryDetailAction.Back -> send(LearnCategoryDetailEffect.NavigateBack)
            LearnCategoryDetailAction.Retry -> load(forceRefresh = true)
            is LearnCategoryDetailAction.OpenLesson -> openLesson(action.lessonId)
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> {
                    catalog = result.data
                    val category = result.data.categories.firstOrNull { it.id == categoryId }
                    val lessons = result.data.lessons.filter { it.categoryId == categoryId }
                    updateState {
                        copy(
                            title = category?.title.orEmpty(),
                            description = "",
                            lessons = lessons,
                            isLoading = false,
                        )
                    }
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun openLesson(id: String) {
        val lesson = catalog?.lessons?.firstOrNull { it.id == id } ?: return
        val steps = lesson.steps.sortedBy { it.stepNumber }.map { it.image }
        val completed = state.value.completedSteps[lesson.id].orZero()
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
                lessonStepCount = steps.size.takeIf { it > 0 } ?: lesson.totalSteps,
                mode = DrawingMode.CAMERA,
            ),
        )
        send(LearnCategoryDetailEffect.NavigateTutorial)
    }

    private fun send(effect: LearnCategoryDetailEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}

private fun Int?.orZero() = this ?: 0
