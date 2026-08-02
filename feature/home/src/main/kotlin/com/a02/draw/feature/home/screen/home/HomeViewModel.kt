package com.a02.draw.feature.home.screen.home

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.feature.home.common.model.DeviceImageSource
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
    private val drawingSessionStore: DrawingSessionStore,
) : BaseViewModel<HomeScreenUiState, HomeScreenEffect>(HomeScreenUiState()) {
    private var loadJob: Job? = null

    init {
        loadCatalog()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Retry -> loadCatalog(forceRefresh = true)
            HomeAction.OpenSearch -> send(HomeScreenEffect.NavigateSearch)
            HomeAction.OpenSourceModal -> updateState { copy(isSourceModalVisible = true) }
            HomeAction.CloseSourceModal -> updateState { copy(isSourceModalVisible = false) }
            HomeAction.OpenAiGallery -> send(HomeScreenEffect.NavigateGallery(null))
            HomeAction.OpenWebSearch -> send(HomeScreenEffect.OpenWebSearch)
            is HomeAction.OpenTopic -> send(HomeScreenEffect.NavigateGallery(action.topicId))
            is HomeAction.SelectSource -> updateState { copy(selectedSource = action.source) }
            HomeAction.ConfirmSource -> {
                updateState { copy(isSourceModalVisible = false) }
                send(
                    if (state.value.selectedSource == DeviceImageSource.GALLERY) {
                        HomeScreenEffect.OpenPhotoPicker
                    } else {
                        HomeScreenEffect.OpenSourceCamera
                    },
                )
            }

            is HomeAction.MediaSelected -> {
                drawingSessionStore.reset(
                    DrawingSession(
                        pickedImageUri = action.uri,
                        mode = DrawingMode.CAMERA,
                    ),
                )
                send(HomeScreenEffect.NavigateTutorial)
            }

            is HomeAction.OpenBottomDestination -> send(HomeScreenEffect.NavigateBottom(action.destination))
        }
    }

    private fun loadCatalog(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> updateState {
                    copy(topics = result.data.topics.take(6), isLoading = false)
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun send(effect: HomeScreenEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
