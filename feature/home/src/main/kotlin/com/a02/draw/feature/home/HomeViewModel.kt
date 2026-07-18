package com.a02.draw.feature.home

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.domain.usecase.SaveDrawingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeDrawings: ObserveDrawingsUseCase,
    private val saveDrawing: SaveDrawingUseCase,
) : BaseViewModel<HomeUiState, HomeEffect>(HomeUiState()) {
    private var observationJob: Job? = null

    init {
        startObserving()
    }

    fun onRetryClicked() {
        startObserving()
    }

    fun onAddDrawingClicked() {
        viewModelScope.launch {
            val drawing = Drawing(
                title = "Untitled sketch",
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            when (saveDrawing(drawing)) {
                is AppResult.Success -> sendEffect(HomeEffect.ShowMessage(R.string.drawing_created))
                is AppResult.Failure -> sendEffect(HomeEffect.ShowMessage(R.string.generic_error))
            }
        }
    }

    private fun startObserving() {
        observationJob?.cancel()
        updateState { copy(isLoading = true, hasError = false) }
        observationJob = viewModelScope.launch {
            observeDrawings()
                .catch {
                    updateState { copy(isLoading = false, hasError = true) }
                }
                .collect { drawings ->
                    updateState {
                        copy(isLoading = false, drawings = drawings, hasError = false)
                    }
                }
        }
    }
}
