package com.a02.draw.feature.home.screen.home

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.model.DeviceImageSource

data class HomeScreenUiState(
    val topics: List<DrawingTopic> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val isSourceModalVisible: Boolean = false,
    val selectedSource: DeviceImageSource = DeviceImageSource.CAMERA,
) : UiState

sealed interface HomeAction {
    data object Retry : HomeAction
    data object OpenSearch : HomeAction
    data object OpenSourceModal : HomeAction
    data object CloseSourceModal : HomeAction
    data object OpenAiGallery : HomeAction
    data object OpenWebSearch : HomeAction
    data class OpenTopic(val topicId: String) : HomeAction
    data class SelectSource(val source: DeviceImageSource) : HomeAction
    data object ConfirmSource : HomeAction
    data class MediaSelected(val uri: String) : HomeAction
    data class OpenBottomDestination(val destination: BottomDestination) : HomeAction
}

sealed interface HomeScreenEffect : UiEffect {
    data object NavigateSearch : HomeScreenEffect
    data class NavigateGallery(val topicId: String?) : HomeScreenEffect
    data object NavigateTutorial : HomeScreenEffect
    data object OpenPhotoPicker : HomeScreenEffect
    data object OpenSourceCamera : HomeScreenEffect
    data object OpenWebSearch : HomeScreenEffect
    data class NavigateBottom(val destination: BottomDestination) : HomeScreenEffect
}
