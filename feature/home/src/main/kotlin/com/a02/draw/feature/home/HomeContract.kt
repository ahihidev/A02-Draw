package com.a02.draw.feature.home

import androidx.annotation.StringRes
import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ArtworkStyle
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.model.DrawingLesson
import com.a02.draw.domain.model.LessonCategory

enum class ArDrawScreen {
    ONBOARDING_PROJECTOR,
    ONBOARDING_LIGHTBOX,
    ONBOARDING_LESSONS,
    ONBOARDING_TOPICS,
    ONBOARDING_LOADING,
    ONBOARDING_PAYWALL,
    HOME,
    HOME_SOURCE_MODAL,
    SEARCH,
    SEARCH_RESULTS,
    GALLERY,
    FILTER,
    SETTINGS,
    SETTINGS_DETAIL,
    LEARN_PATH,
    LEARN_LEVEL_DETAIL,
    LEARN_CATEGORIES,
    LEARN_CATEGORY_DETAIL,
    PROFILE_FAVORITE_EMPTY,
    PROFILE_ALBUM_EMPTY,
    PROFILE_FAVORITE,
    PROFILE_ALBUM,
    TUTORIAL_CAMERA,
    TUTORIAL_SCREEN,
    DRAWING_CANVAS,
    DRAWING_CAMERA,
    DRAWING_OPACITY,
    DRAWING_COMPLETE,
}

enum class BottomDestination { HOME, LEARN, PROFILE, SETTINGS }
enum class DeviceImageSource { GALLERY, CAMERA }
enum class GalleryFilter {
    SAVED,
    ALL,
    EASY,
    PREMIUM,
    JUJUTSU_KAISEN,
    ONE_PIECE,
    DORAEMON,
}
enum class DrawingCropRatio { RESET, SQUARE, PORTRAIT, LANDSCAPE }
enum class DrawingCanvasPanel { NONE, CROP, GRID }
enum class DrawingCameraPanel { NONE, ZOOM, FLASH, CAPTURE, RECORD, RATIO }
enum class DrawingCameraRatio { FULL, RATIO_16_9, RATIO_4_3, SQUARE }
enum class DrawingStatus { NONE, LOCK, FLIP, REMOVE_IMAGE, FLASH }

sealed interface ArDrawAction {
    data object Continue : ArDrawAction
    data class SelectPlan(val planId: String) : ArDrawAction
    data object RetryContent : ArDrawAction
    data class ToggleTopic(val index: Int) : ArDrawAction
    data class OpenBottomDestination(val destination: BottomDestination) : ArDrawAction
    data object OpenSourceModal : ArDrawAction
    data object OpenAiEmojiMix : ArDrawAction
    data object OpenWebBrowser : ArDrawAction
    data class SelectDeviceSource(val source: DeviceImageSource) : ArDrawAction
    data object ConfirmSource : ArDrawAction
    data class MediaPicked(val uri: String) : ArDrawAction
    data object OpenSearch : ArDrawAction
    data class SearchQueryChanged(val query: String) : ArDrawAction
    data object SubmitSearch : ArDrawAction
    data class OpenGallery(val topicId: String? = null) : ArDrawAction
    data object OpenFilter : ArDrawAction
    data class SelectGalleryFilter(val filter: GalleryFilter) : ArDrawAction
    data class SelectDifficulty(val value: String?) : ArDrawAction
    data class SelectDrawingStyle(val value: ArtworkStyle) : ArDrawAction
    data object ClearFilters : ArDrawAction
    data object ApplyFilter : ArDrawAction
    data class SelectArtwork(val artworkId: String) : ArDrawAction
    data class ToggleFavorite(val artworkId: String) : ArDrawAction
    data object OpenLearnPath : ArDrawAction
    data object OpenLearnCategories : ArDrawAction
    data class OpenLearnDetail(val id: String? = null) : ArDrawAction
    data class OpenLessonTutorial(val lessonId: String) : ArDrawAction
    data object OpenProfileFavorite : ArDrawAction
    data object OpenProfileAlbum : ArDrawAction
    data object OpenTutorial : ArDrawAction
    data class SelectTutorialMode(val useCamera: Boolean) : ArDrawAction
    data object StartDrawing : ArDrawAction
    data class CameraPermissionResult(val granted: Boolean) : ArDrawAction
    data class SyncCameraPermission(val granted: Boolean) : ArDrawAction
    data class SelectDrawingTool(val screen: ArDrawScreen) : ArDrawAction
    data class ChangeOpacity(val opacity: Float) : ArDrawAction
    data class ChangeZoom(val zoom: Float) : ArDrawAction
    data class MoveOverlay(val deltaX: Float, val deltaY: Float) : ArDrawAction
    data class SetOverlayTransform(val offsetX: Float, val offsetY: Float, val zoom: Float) :
        ArDrawAction

    data object ToggleFlash : ArDrawAction
    data object ToggleLock : ArDrawAction
    data object ToggleFlip : ArDrawAction
    data object ToggleRemoveImage : ArDrawAction
    data class OpenCanvasPanel(val panel: DrawingCanvasPanel) : ArDrawAction
    data class SelectCropRatio(val ratio: DrawingCropRatio) : ArDrawAction
    data class SelectGridSize(val size: Int) : ArDrawAction
    data class OpenCameraPanel(val panel: DrawingCameraPanel) : ArDrawAction
    data class SelectCameraZoom(val zoom: Float) : ArDrawAction
    data class SelectCameraRatio(val ratio: DrawingCameraRatio) : ArDrawAction
    data object ToggleRecording : ArDrawAction
    data object RecordingFinished : ArDrawAction
    data object ToggleOverlay : ArDrawAction
    data object CaptureDrawing : ArDrawAction
    data class PhotoCaptured(val uri: String) : ArDrawAction
    data object CompleteDrawing : ArDrawAction
    data object ShareDrawing : ArDrawAction
    data object RetakeDrawing : ArDrawAction
    data class OpenDrawing(val drawingId: Long) : ArDrawAction
    data class OpenSetting(val settingId: String) : ArDrawAction
    data object Back : ArDrawAction
}

data class HomeUiState(
    val screen: ArDrawScreen = ArDrawScreen.ONBOARDING_PROJECTOR,
    val catalog: ArCatalog? = null,
    val isLoading: Boolean = true,
    val contentError: Boolean = false,
    val selectedTopicIds: Set<String> = emptySet(),
    val selectedPlanId: String? = null,
    val selectedSettingId: String? = null,
    val paywallOrigin: ArDrawScreen? = null,
    val selectedGalleryTopicId: String? = null,
    val selectedArtworkId: String? = null,
    val selectedLessonId: String? = null,
    val selectedCategoryId: String? = null,
    val selectedDeviceSource: DeviceImageSource = DeviceImageSource.CAMERA,
    val selectedDifficulty: String? = null,
    val selectedDrawingStyle: ArtworkStyle? = null,
    val selectedGalleryFilter: GalleryFilter = GalleryFilter.ALL,
    val searchOrigin: ArDrawScreen = ArDrawScreen.HOME,
    val tutorialOrigin: ArDrawScreen = ArDrawScreen.GALLERY,
    val searchQuery: String = "",
    val favoriteArtworkIds: Set<String> = emptySet(),
    val drawings: List<Drawing> = emptyList(),
    val musicEnabled: Boolean = true,
    val pickedImageUri: String? = null,
    val capturedImageUri: String? = null,
    val replacedMediaUri: String? = null,
    val activeDrawingId: Long? = null,
    val drawingCompleteOrigin: ArDrawScreen? = null,
    val cameraPermissionGranted: Boolean = false,
    val opacity: Float = 0.4f,
    val zoom: Float = 1f,
    val overlayOffsetX: Float = 0f,
    val overlayOffsetY: Float = 0f,
    val cameraZoom: Float = 1f,
    val flashEnabled: Boolean = false,
    val overlayLocked: Boolean = false,
    val overlayFlipped: Boolean = false,
    val removeImageEnabled: Boolean = false,
    val canvasPanel: DrawingCanvasPanel = DrawingCanvasPanel.NONE,
    val cropRatio: DrawingCropRatio = DrawingCropRatio.RESET,
    val gridSize: Int = 0,
    val cameraPanel: DrawingCameraPanel = DrawingCameraPanel.NONE,
    val cameraRatio: DrawingCameraRatio = DrawingCameraRatio.FULL,
    val isRecording: Boolean = false,
    val drawingStatus: DrawingStatus = DrawingStatus.NONE,
    val overlayVisible: Boolean = true,
    val drawingWithCamera: Boolean = true,
) : UiState {
    val handlesBackInApp: Boolean
        get() = screen != ArDrawScreen.HOME && screen != ArDrawScreen.ONBOARDING_PROJECTOR

    val selectedArtwork: Artwork?
        get() = catalog?.artworks?.firstOrNull { it.id == selectedArtworkId }

    val selectedLesson: DrawingLesson?
        get() = catalog?.lessons?.firstOrNull { it.id == selectedLessonId }

    val selectedCategory: LessonCategory?
        get() = catalog?.categories?.firstOrNull { it.id == selectedCategoryId }

    val selectedReferenceImage: ContentImage?
        get() = selectedLesson?.image ?: selectedCategory?.image ?: selectedArtwork?.image

    val selectedTraceImage: ContentImage?
        get() = selectedLesson?.image ?: selectedCategory?.image
        ?: selectedArtwork?.traceImage ?: selectedArtwork?.image

    val selectedReferenceTitle: String?
        get() = selectedLesson?.title ?: selectedCategory?.title ?: selectedArtwork?.title

    val learningPathLessons: List<DrawingLesson>
        get() = catalog?.lessons.orEmpty().filter(DrawingLesson::showInLearningPath)

    val completedLessonCount: Int
        get() = drawings.mapNotNull(Drawing::lessonId).distinct().size

    val completedLessonMinutes: Int
        get() = drawings
            .filter { it.lessonId != null }
            .distinctBy(Drawing::lessonId)
            .sumOf { it.lessonMinutes ?: 0 }

    fun lessonProgressPercent(lesson: DrawingLesson): Int =
        if (drawings.any { it.lessonId == lesson.id }) {
            100
        } else {
            lesson.completedPercent.coerceIn(0, 100)
        }

    val visibleArtworks: List<Artwork>
        get() {
            val query = searchQuery.trim()
            return catalog?.artworks.orEmpty().filter { artwork ->
                val queryMatches = query.isBlank() || artwork.title.contains(query, true) ||
                        artwork.tags.any { it.contains(query, true) }
                queryMatches
            }
        }

    val visibleGalleryArtworks: List<Artwork>
        get() = catalog?.artworks.orEmpty().filter { artwork ->
            val topicMatches =
                selectedGalleryTopicId == null || artwork.topicId == selectedGalleryTopicId
            val quickFilterMatches = when (selectedGalleryFilter) {
                GalleryFilter.SAVED -> artwork.id in favoriteArtworkIds
                GalleryFilter.ALL -> true
                GalleryFilter.EASY -> artwork.difficulty.equals("Easy", ignoreCase = true)
                GalleryFilter.PREMIUM -> artwork.isPremium
                GalleryFilter.JUJUTSU_KAISEN -> "jujutsu-kaisen" in artwork.tags
                GalleryFilter.ONE_PIECE -> "one-piece" in artwork.tags
                GalleryFilter.DORAEMON -> "doraemon" in artwork.tags
            }
            val difficultyMatches = selectedDifficulty == null ||
                    artwork.difficulty.equals(selectedDifficulty, ignoreCase = true)
            val styleMatches = selectedDrawingStyle == null || artwork.style == selectedDrawingStyle
            topicMatches && quickFilterMatches && difficultyMatches && styleMatches
        }
}

sealed interface HomeEffect : UiEffect {
    data class ShowMessage(@param:StringRes val messageRes: Int) : HomeEffect
    data object OpenPhotoPicker : HomeEffect
    data object OpenSourceCamera : HomeEffect
    data object RequestCameraPermission : HomeEffect
    data object CapturePhoto : HomeEffect
    data object CaptureCanvas : HomeEffect
    data class SetTorch(val enabled: Boolean) : HomeEffect
    data class SetCameraZoom(val zoom: Float) : HomeEffect
    data class SetRecording(val enabled: Boolean) : HomeEffect
    data class Share(val uri: String?) : HomeEffect
    data class DeleteMedia(val uri: String) : HomeEffect
    data class OpenExternal(val target: String) : HomeEffect
    data object OpenStoreListing : HomeEffect
    data object OpenSubscriptionManager : HomeEffect
    data object FocusSearch : HomeEffect
}
