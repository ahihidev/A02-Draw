package com.a02.draw.feature.home

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.model.SettingType
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.domain.usecase.SaveDrawingUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
    private val observeAppPreferences: ObserveAppPreferencesUseCase,
    private val updatePreferences: UpdateAppPreferencesUseCase,
    private val observeDrawings: ObserveDrawingsUseCase,
    private val saveDrawing: SaveDrawingUseCase,
) : BaseViewModel<HomeUiState, HomeEffect>(HomeUiState()) {
    private var onboardingJob: Job? = null
    private var catalogLoadJob: Job? = null
    private var preferencesResolved = false
    private val drawingSaveMutex = Mutex()
    private var drawingSessionGeneration = 0L

    init {
        loadCatalog()
        collectPreferences()
        observeSavedDrawings()
    }

    fun onAction(action: ArDrawAction) {
        when (action) {
            ArDrawAction.Continue -> continueFlow()
            is ArDrawAction.SelectPlan -> updateState { copy(selectedPlanId = action.planId) }
            ArDrawAction.RetryContent -> loadCatalog(forceRefresh = true)
            is ArDrawAction.ToggleTopic -> toggleTopic(action.index)
            is ArDrawAction.OpenBottomDestination -> openBottomDestination(action.destination)
            ArDrawAction.OpenSourceModal -> show(ArDrawScreen.HOME_SOURCE_MODAL)
            ArDrawAction.OpenAiEmojiMix -> updateState {
                copy(screen = ArDrawScreen.GALLERY, selectedGalleryTopicId = null)
            }

            ArDrawAction.OpenWebBrowser -> viewModelScope.launch {
                sendEffect(HomeEffect.OpenExternal(WEB_IMAGE_SEARCH_URL))
            }

            is ArDrawAction.SelectDeviceSource -> updateState { copy(selectedDeviceSource = action.source) }
            ArDrawAction.ConfirmSource -> confirmSource()
            is ArDrawAction.MediaPicked -> {
                beginDrawingSession()
                updateState {
                    copy(
                        pickedImageUri = action.uri,
                        selectedArtworkId = null,
                        selectedLessonId = null,
                        selectedCategoryId = null,
                        capturedImageUri = null,
                        replacedMediaUri = null,
                        activeDrawingId = null,
                        drawingCompleteOrigin = null,
                        screen = ArDrawScreen.TUTORIAL_CAMERA,
                        drawingWithCamera = true,
                        tutorialOrigin = ArDrawScreen.HOME,
                    )
                }
            }

            ArDrawAction.OpenSearch -> openSearch()
            is ArDrawAction.SearchQueryChanged -> updateState { copy(searchQuery = action.query) }
            ArDrawAction.SubmitSearch -> show(ArDrawScreen.SEARCH_RESULTS)
            is ArDrawAction.OpenGallery -> updateState {
                copy(screen = ArDrawScreen.GALLERY, selectedGalleryTopicId = action.topicId)
            }

            ArDrawAction.OpenFilter -> show(ArDrawScreen.FILTER)
            is ArDrawAction.SelectGalleryFilter -> updateState {
                copy(
                    selectedGalleryFilter = if (
                        selectedGalleryFilter == action.filter && action.filter != GalleryFilter.ALL
                    ) GalleryFilter.ALL else action.filter,
                )
            }

            is ArDrawAction.SelectDifficulty -> updateState {
                copy(selectedDifficulty = if (selectedDifficulty == action.value) null else action.value)
            }

            is ArDrawAction.SelectDrawingStyle -> updateState {
                copy(selectedDrawingStyle = if (selectedDrawingStyle == action.value) null else action.value)
            }

            ArDrawAction.ClearFilters -> updateState {
                copy(
                    selectedDifficulty = null,
                    selectedDrawingStyle = null,
                    selectedGalleryFilter = GalleryFilter.ALL,
                )
            }

            ArDrawAction.ApplyFilter -> show(ArDrawScreen.GALLERY)
            is ArDrawAction.SelectArtwork -> selectArtwork(action.artworkId)
            is ArDrawAction.ToggleFavorite -> toggleFavorite(action.artworkId)
            ArDrawAction.OpenLearnPath -> show(ArDrawScreen.LEARN_PATH)
            ArDrawAction.OpenLearnCategories -> show(ArDrawScreen.LEARN_CATEGORIES)
            is ArDrawAction.OpenLearnDetail -> openLearnDetail(action.id)
            is ArDrawAction.OpenLessonTutorial -> openLessonTutorial(action.lessonId)
            ArDrawAction.OpenProfileFavorite -> show(profileFavoriteScreen())
            ArDrawAction.OpenProfileAlbum -> show(profileAlbumScreen())
            ArDrawAction.OpenTutorial -> updateState {
                copy(screen = ArDrawScreen.TUTORIAL_CAMERA, tutorialOrigin = screen)
            }

            is ArDrawAction.SelectTutorialMode -> updateState {
                copy(
                    screen = if (action.useCamera) ArDrawScreen.TUTORIAL_CAMERA else ArDrawScreen.TUTORIAL_SCREEN,
                    drawingWithCamera = action.useCamera,
                )
            }

            ArDrawAction.StartDrawing -> startDrawing()
            is ArDrawAction.CameraPermissionResult -> handleCameraPermission(action.granted)
            is ArDrawAction.SyncCameraPermission -> updateState { copy(cameraPermissionGranted = action.granted) }
            is ArDrawAction.SelectDrawingTool -> selectDrawingTool(action.screen)
            is ArDrawAction.ChangeOpacity -> updateState {
                copy(
                    opacity = action.opacity.coerceIn(
                        0.1f,
                        1f
                    )
                )
            }

            is ArDrawAction.ChangeZoom -> updateState {
                copy(
                    zoom = action.zoom.coerceIn(
                        0.5f,
                        3f
                    )
                )
            }

            is ArDrawAction.MoveOverlay -> updateState {
                copy(
                    overlayOffsetX = (overlayOffsetX + action.deltaX).coerceIn(-150f, 150f),
                    overlayOffsetY = (overlayOffsetY + action.deltaY).coerceIn(-220f, 220f),
                )
            }

            is ArDrawAction.SetOverlayTransform -> updateState {
                copy(
                    overlayOffsetX = action.offsetX.coerceIn(-150f, 150f),
                    overlayOffsetY = action.offsetY.coerceIn(-220f, 220f),
                    zoom = action.zoom.coerceIn(0.5f, 3f),
                )
            }

            ArDrawAction.ToggleFlash -> toggleFlash()
            ArDrawAction.ToggleLock -> updateState {
                copy(overlayLocked = !overlayLocked, drawingStatus = DrawingStatus.LOCK)
            }

            ArDrawAction.ToggleFlip -> updateState {
                copy(overlayFlipped = !overlayFlipped, drawingStatus = DrawingStatus.FLIP)
            }

            ArDrawAction.ToggleRemoveImage -> updateState {
                copy(
                    removeImageEnabled = !removeImageEnabled,
                    drawingStatus = DrawingStatus.REMOVE_IMAGE
                )
            }

            is ArDrawAction.OpenCanvasPanel -> updateState {
                copy(
                    canvasPanel = if (canvasPanel == action.panel) DrawingCanvasPanel.NONE else action.panel,
                    drawingStatus = DrawingStatus.NONE,
                )
            }

            is ArDrawAction.SelectCropRatio -> updateState {
                copy(cropRatio = action.ratio, canvasPanel = DrawingCanvasPanel.CROP)
            }

            is ArDrawAction.SelectGridSize -> updateState {
                val requestedSize = action.size.coerceIn(3, 5)
                if (gridSize == requestedSize) {
                    copy(gridSize = 0, canvasPanel = DrawingCanvasPanel.NONE)
                } else {
                    copy(gridSize = requestedSize, canvasPanel = DrawingCanvasPanel.GRID)
                }
            }

            is ArDrawAction.OpenCameraPanel -> openCameraPanel(action.panel)
            is ArDrawAction.SelectCameraZoom -> {
                val zoom = action.zoom.coerceIn(0.5f, 3f)
                updateState { copy(cameraZoom = zoom, cameraPanel = DrawingCameraPanel.ZOOM) }
                viewModelScope.launch { sendEffect(HomeEffect.SetCameraZoom(zoom)) }
            }

            is ArDrawAction.SelectCameraRatio -> updateState {
                copy(cameraRatio = action.ratio, cameraPanel = DrawingCameraPanel.RATIO)
            }

            ArDrawAction.ToggleRecording -> {
                val recording = !state.value.isRecording
                updateState {
                    copy(
                        isRecording = recording,
                        cameraPanel = DrawingCameraPanel.RECORD
                    )
                }
                viewModelScope.launch { sendEffect(HomeEffect.SetRecording(recording)) }
            }

            ArDrawAction.RecordingFinished -> updateState { copy(isRecording = false) }
            ArDrawAction.ToggleOverlay -> updateState { copy(overlayVisible = !overlayVisible) }
            ArDrawAction.CaptureDrawing -> captureDrawing()
            is ArDrawAction.PhotoCaptured -> onPhotoCaptured(action.uri)
            ArDrawAction.CompleteDrawing -> completeDrawing()
            ArDrawAction.ShareDrawing -> shareDrawing()
            ArDrawAction.RetakeDrawing -> retakeDrawing()

            is ArDrawAction.OpenDrawing -> openSavedDrawing(action.drawingId)
            is ArDrawAction.OpenSetting -> openSetting(action.settingId)
            ArDrawAction.Back -> goBack()
        }
    }

    /** Backwards-compatible entry point retained for existing callers and tests. */
    fun onAddDrawingClicked() = saveCurrentDrawing(state.value.capturedImageUri)

    private fun loadCatalog(forceRefresh: Boolean = false) {
        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, contentError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> updateState {
                    copy(
                        catalog = result.data,
                        selectedArtworkId = selectedArtworkId
                            ?: result.data.artworks.firstOrNull()?.id,
                        selectedPlanId = selectedPlanId
                            ?: result.data.plans.firstOrNull { it.isRecommended }?.id
                            ?: result.data.plans.firstOrNull()?.id,
                        isLoading = false,
                    )
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, contentError = true) }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            observeAppPreferences()
                .catch { sendEffect(HomeEffect.ShowMessage(R.string.generic_error)) }
                .collect { preferences ->
                    updateState {
                        copy(
                            favoriteArtworkIds = preferences.favoriteArtworkIds,
                            musicEnabled = preferences.musicEnabled,
                            screen = if (!preferencesResolved && preferences.onboardingCompleted &&
                                screen == ArDrawScreen.ONBOARDING_PROJECTOR
                            ) {
                                ArDrawScreen.HOME
                            } else {
                                screen
                            },
                        )
                    }
                    preferencesResolved = true
                }
        }
    }

    private fun observeSavedDrawings() {
        viewModelScope.launch {
            observeDrawings()
                .catch { sendEffect(HomeEffect.ShowMessage(R.string.generic_error)) }
                .collect { drawings ->
                    updateState {
                        val profileScreen = when (screen) {
                            ArDrawScreen.PROFILE_ALBUM,
                            ArDrawScreen.PROFILE_ALBUM_EMPTY,
                                -> if (drawings.isEmpty()) ArDrawScreen.PROFILE_ALBUM_EMPTY else ArDrawScreen.PROFILE_ALBUM

                            else -> screen
                        }
                        copy(drawings = drawings, screen = profileScreen)
                    }
                }
        }
    }

    private fun continueFlow() {
        when (state.value.screen) {
            ArDrawScreen.ONBOARDING_PROJECTOR -> show(ArDrawScreen.ONBOARDING_LIGHTBOX)
            ArDrawScreen.ONBOARDING_LIGHTBOX -> show(ArDrawScreen.ONBOARDING_LESSONS)
            ArDrawScreen.ONBOARDING_LESSONS -> show(ArDrawScreen.ONBOARDING_TOPICS)
            ArDrawScreen.ONBOARDING_TOPICS -> beginPersonalization()
            ArDrawScreen.ONBOARDING_LOADING -> finishPersonalization()
            ArDrawScreen.ONBOARDING_PAYWALL -> {
                val origin = state.value.paywallOrigin
                updateState { copy(screen = origin ?: ArDrawScreen.HOME, paywallOrigin = null) }
                if (origin == null) {
                    viewModelScope.launch { updatePreferences.setOnboardingCompleted(true) }
                }
            }

            else -> Unit
        }
    }

    private fun beginPersonalization() {
        show(ArDrawScreen.ONBOARDING_LOADING)
        onboardingJob?.cancel()
        onboardingJob = viewModelScope.launch {
            delay(PERSONALIZATION_DELAY_MILLIS)
            if (state.value.screen == ArDrawScreen.ONBOARDING_LOADING) finishPersonalization()
        }
    }

    private fun finishPersonalization() {
        if (state.value.catalog?.plans.orEmpty().isNotEmpty()) {
            show(ArDrawScreen.ONBOARDING_PAYWALL)
        } else {
            show(ArDrawScreen.HOME)
            persistOnboardingCompletion()
        }
    }

    private fun toggleTopic(index: Int) {
        val id = state.value.catalog?.topics?.getOrNull(index)?.id ?: return
        updateState {
            val next = selectedTopicIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            while (next.size > MAX_TOPICS) next.remove(next.first())
            copy(selectedTopicIds = next)
        }
    }

    private fun confirmSource() {
        if (state.value.selectedDeviceSource == DeviceImageSource.GALLERY) {
            viewModelScope.launch { sendEffect(HomeEffect.OpenPhotoPicker) }
        } else {
            viewModelScope.launch { sendEffect(HomeEffect.OpenSourceCamera) }
        }
    }

    private fun openSearch() {
        updateState { copy(screen = ArDrawScreen.SEARCH, searchOrigin = screen) }
        viewModelScope.launch { sendEffect(HomeEffect.FocusSearch) }
    }

    private fun selectArtwork(id: String) {
        beginDrawingSession()
        updateState {
            copy(
                selectedArtworkId = id,
                selectedLessonId = null,
                selectedCategoryId = null,
                pickedImageUri = null,
                capturedImageUri = null,
                replacedMediaUri = null,
                activeDrawingId = null,
                drawingCompleteOrigin = null,
                screen = ArDrawScreen.TUTORIAL_CAMERA,
                drawingWithCamera = true,
                tutorialOrigin = screen,
            )
        }
    }

    private fun toggleFavorite(id: String) {
        val previous = state.value.favoriteArtworkIds
        val next = previous.toMutableSet().apply { if (!add(id)) remove(id) }
        updateState {
            copy(
                favoriteArtworkIds = next,
                screen = if (screen == ArDrawScreen.PROFILE_FAVORITE ||
                    screen == ArDrawScreen.PROFILE_FAVORITE_EMPTY
                ) {
                    profileFavoriteScreen(next)
                } else {
                    screen
                },
            )
        }
        viewModelScope.launch {
            if (updatePreferences.setFavorites(next) is AppResult.Failure) {
                updateState {
                    if (favoriteArtworkIds == next) copy(favoriteArtworkIds = previous) else this
                }
                sendEffect(HomeEffect.ShowMessage(R.string.generic_error))
            }
        }
    }

    private fun openBottomDestination(destination: BottomDestination) {
        show(
            when (destination) {
                BottomDestination.HOME -> ArDrawScreen.HOME
                BottomDestination.LEARN -> ArDrawScreen.LEARN_PATH
                BottomDestination.PROFILE -> profileFavoriteScreen()
                BottomDestination.SETTINGS -> ArDrawScreen.SETTINGS
            },
        )
    }

    private fun openLearnDetail(id: String?) {
        beginDrawingSession()
        updateState {
            if (screen == ArDrawScreen.LEARN_CATEGORIES) {
                val categoryId = id ?: catalog?.categories?.firstOrNull()?.id
                copy(
                    selectedCategoryId = categoryId,
                    selectedArtworkId = null,
                    selectedLessonId = catalog?.lessons
                        ?.firstOrNull { it.categoryId == categoryId }
                        ?.id,
                    pickedImageUri = null,
                    capturedImageUri = null,
                    replacedMediaUri = null,
                    activeDrawingId = null,
                    drawingCompleteOrigin = null,
                    screen = ArDrawScreen.LEARN_CATEGORY_DETAIL,
                )
            } else {
                copy(
                    selectedCategoryId = null,
                    selectedArtworkId = null,
                    selectedLessonId = id ?: catalog?.lessons?.firstOrNull()?.id,
                    pickedImageUri = null,
                    capturedImageUri = null,
                    replacedMediaUri = null,
                    activeDrawingId = null,
                    drawingCompleteOrigin = null,
                    screen = ArDrawScreen.LEARN_LEVEL_DETAIL,
                )
            }
        }
    }

    private fun openLessonTutorial(lessonId: String) {
        val lesson = state.value.catalog?.lessons?.firstOrNull { it.id == lessonId } ?: return
        beginDrawingSession()
        updateState {
            copy(
                selectedLessonId = lesson.id,
                selectedArtworkId = null,
                pickedImageUri = null,
                capturedImageUri = null,
                replacedMediaUri = null,
                activeDrawingId = null,
                drawingCompleteOrigin = null,
                tutorialOrigin = screen,
                drawingWithCamera = true,
                screen = ArDrawScreen.TUTORIAL_CAMERA,
            )
        }
    }

    private fun startDrawing() {
        if (state.value.screen == ArDrawScreen.TUTORIAL_SCREEN) {
            updateState {
                copy(
                    screen = ArDrawScreen.DRAWING_CANVAS,
                    drawingWithCamera = false,
                    drawingCompleteOrigin = null,
                    overlayOffsetX = 0f,
                    overlayOffsetY = 0f,
                    zoom = 1f,
                    overlayVisible = true,
                    opacity = DEFAULT_OPACITY,
                    overlayLocked = false,
                    overlayFlipped = false,
                    removeImageEnabled = false,
                    canvasPanel = DrawingCanvasPanel.NONE,
                    cropRatio = DrawingCropRatio.RESET,
                    gridSize = 0,
                    drawingStatus = DrawingStatus.NONE,
                )
            }
        } else if (state.value.cameraPermissionGranted) {
            updateState {
                copy(
                    screen = ArDrawScreen.DRAWING_CAMERA,
                    drawingWithCamera = true,
                    drawingCompleteOrigin = null,
                    overlayOffsetX = 0f,
                    overlayOffsetY = 0f,
                    zoom = 1f,
                    overlayVisible = true,
                    opacity = DEFAULT_OPACITY,
                    overlayLocked = false,
                    overlayFlipped = false,
                    removeImageEnabled = false,
                    canvasPanel = DrawingCanvasPanel.NONE,
                    cropRatio = DrawingCropRatio.RESET,
                    gridSize = 0,
                    cameraPanel = DrawingCameraPanel.NONE,
                    cameraRatio = DrawingCameraRatio.FULL,
                    cameraZoom = 1f,
                    flashEnabled = false,
                    isRecording = false,
                    drawingStatus = DrawingStatus.NONE,
                )
            }
        } else {
            viewModelScope.launch { sendEffect(HomeEffect.RequestCameraPermission) }
        }
    }

    private fun selectDrawingTool(screen: ArDrawScreen) {
        updateState { copy(screen = screen) }
    }

    private fun handleCameraPermission(granted: Boolean) {
        updateState { copy(cameraPermissionGranted = granted) }
        if (granted) enterCameraDrawing() else {
            viewModelScope.launch { sendEffect(HomeEffect.ShowMessage(R.string.camera_permission_required)) }
        }
    }

    private fun enterCameraDrawing() {
        updateState {
            copy(
                screen = ArDrawScreen.DRAWING_CAMERA,
                drawingWithCamera = true,
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                zoom = 1f,
                overlayVisible = true,
                opacity = DEFAULT_OPACITY,
                overlayLocked = false,
                overlayFlipped = false,
                removeImageEnabled = false,
                canvasPanel = DrawingCanvasPanel.NONE,
                cropRatio = DrawingCropRatio.RESET,
                gridSize = 0,
                cameraPanel = DrawingCameraPanel.NONE,
                cameraRatio = DrawingCameraRatio.FULL,
                cameraZoom = 1f,
                flashEnabled = false,
                isRecording = false,
                drawingStatus = DrawingStatus.NONE,
            )
        }
    }

    private fun toggleFlash() {
        val enabled = !state.value.flashEnabled
        updateState {
            copy(
                flashEnabled = enabled,
                cameraPanel = DrawingCameraPanel.FLASH,
                drawingStatus = DrawingStatus.FLASH,
            )
        }
        viewModelScope.launch { sendEffect(HomeEffect.SetTorch(enabled)) }
    }

    private fun openCameraPanel(panel: DrawingCameraPanel) {
        if (panel != DrawingCameraPanel.FLASH && state.value.cameraPanel == panel) {
            updateState { copy(cameraPanel = DrawingCameraPanel.NONE) }
            return
        }
        when (panel) {
            DrawingCameraPanel.FLASH -> toggleFlash()
            DrawingCameraPanel.CAPTURE,
            DrawingCameraPanel.RECORD,
                -> updateState { copy(cameraPanel = panel) }

            else -> updateState { copy(cameraPanel = panel) }
        }
    }

    private fun captureDrawing() {
        if (!state.value.drawingWithCamera) {
            viewModelScope.launch { sendEffect(HomeEffect.CaptureCanvas) }
        } else if (state.value.cameraPermissionGranted) {
            viewModelScope.launch { sendEffect(HomeEffect.CapturePhoto) }
        } else {
            viewModelScope.launch { sendEffect(HomeEffect.RequestCameraPermission) }
        }
    }

    private fun onPhotoCaptured(uri: String) {
        updateState {
            copy(
                capturedImageUri = uri,
                screen = ArDrawScreen.DRAWING_COMPLETE,
                drawingCompleteOrigin = screen.takeIf { it in DRAWING_SCREENS },
            )
        }
        saveCurrentDrawing(uri)
    }

    private fun completeDrawing() {
        val currentScreen = state.value.screen
        if (currentScreen !in DRAWING_SCREENS) return
        if (state.value.isRecording) {
            viewModelScope.launch { sendEffect(HomeEffect.SetRecording(false)) }
        }
        updateState {
            copy(
                screen = ArDrawScreen.DRAWING_COMPLETE,
                drawingCompleteOrigin = currentScreen,
                isRecording = false,
                capturedImageUri = null,
            )
        }
    }

    private fun retakeDrawing() {
        updateState {
            val returnScreen = drawingCompleteOrigin
                ?.takeIf { it in DRAWING_SCREENS }
                ?: if (drawingWithCamera) {
                    ArDrawScreen.TUTORIAL_CAMERA
                } else {
                    ArDrawScreen.TUTORIAL_SCREEN
                }
            copy(
                replacedMediaUri = capturedImageUri ?: replacedMediaUri,
                capturedImageUri = null,
                screen = returnScreen,
            )
        }
    }

    private fun saveCurrentDrawing(uri: String?) {
        val requestedState = state.value
        val requestedGeneration = drawingSessionGeneration
        viewModelScope.launch {
            drawingSaveMutex.withLock {
                val currentState = state.value
                val sameDrawingFlow = drawingSessionGeneration == requestedGeneration
                val drawing = Drawing(
                    id = requestedState.activeDrawingId
                        ?: currentState.activeDrawingId?.takeIf { sameDrawingFlow }
                        ?: 0,
                    title = requestedState.selectedReferenceTitle
                        ?: "AR sketch ${requestedState.drawings.size + 1}",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    mediaUri = uri,
                    artworkId = requestedState.selectedArtwork
                        ?.takeIf {
                            requestedState.selectedLesson == null &&
                                    requestedState.selectedCategory == null
                        }
                        ?.id,
                    lessonId = requestedState.selectedLesson?.id,
                    lessonMinutes = requestedState.selectedLesson?.minutes,
                    usesCamera = requestedState.drawingWithCamera,
                )
                when (val result = saveDrawing(drawing)) {
                    is AppResult.Success -> {
                        updateState {
                            if (drawingSessionGeneration == requestedGeneration) {
                                copy(activeDrawingId = result.data, replacedMediaUri = null)
                            } else {
                                this
                            }
                        }
                        requestedState.replacedMediaUri
                            ?.takeIf { it != uri }
                            ?.let { sendEffect(HomeEffect.DeleteMedia(it)) }
                        sendEffect(HomeEffect.ShowMessage(R.string.drawing_created))
                    }

                    is AppResult.Failure -> sendEffect(HomeEffect.ShowMessage(R.string.generic_error))
                }
            }
        }
    }

    private fun shareDrawing() {
        viewModelScope.launch { sendEffect(HomeEffect.Share(state.value.capturedImageUri)) }
    }

    private fun openSavedDrawing(drawingId: Long) {
        val drawing = state.value.drawings.firstOrNull { it.id == drawingId } ?: return
        beginDrawingSession()
        updateState {
            copy(
                selectedArtworkId = drawing.artworkId,
                selectedLessonId = drawing.lessonId,
                selectedCategoryId = null,
                pickedImageUri = null,
                capturedImageUri = drawing.mediaUri,
                replacedMediaUri = null,
                activeDrawingId = drawing.id,
                drawingCompleteOrigin = screen,
                tutorialOrigin = screen,
                drawingWithCamera = drawing.usesCamera,
                screen = ArDrawScreen.DRAWING_COMPLETE,
            )
        }
    }

    private fun openSetting(settingId: String) {
        val setting = state.value.catalog?.settings?.firstOrNull { it.id == settingId } ?: return
        when {
            setting.id == "subscription" -> viewModelScope.launch {
                sendEffect(HomeEffect.OpenSubscriptionManager)
            }

            setting.id in setOf("gift", "help", "privacy", "terms") -> updateState {
                copy(screen = ArDrawScreen.SETTINGS_DETAIL, selectedSettingId = setting.id)
            }

            setting.id == "update" -> viewModelScope.launch {
                sendEffect(HomeEffect.OpenStoreListing)
            }

            setting.id == "music" || setting.type == SettingType.TOGGLE -> {
                val previous = state.value.musicEnabled
                val enabled = !previous
                updateState { copy(musicEnabled = enabled) }
                viewModelScope.launch {
                    if (updatePreferences.setMusicEnabled(enabled) is AppResult.Failure) {
                        updateState {
                            if (musicEnabled == enabled) copy(musicEnabled = previous) else this
                        }
                        sendEffect(HomeEffect.ShowMessage(R.string.generic_error))
                    }
                }
            }

            setting.type == SettingType.SHARE -> viewModelScope.launch {
                sendEffect(
                    HomeEffect.Share(
                        null
                    )
                )
            }

            setting.type == SettingType.RATE -> viewModelScope.launch {
                sendEffect(HomeEffect.OpenStoreListing)
            }

            setting.id == "feedback" -> viewModelScope.launch {
                sendEffect(HomeEffect.OpenExternal("mailto:?subject=AR%20Drawing%20feedback"))
            }

            !setting.target.isNullOrBlank() -> setting.target?.let { target ->
                viewModelScope.launch { sendEffect(HomeEffect.OpenExternal(target)) }
            }
        }
    }

    private fun persistOnboardingCompletion() {
        viewModelScope.launch {
            if (updatePreferences.setOnboardingCompleted(true) is AppResult.Failure) {
                sendEffect(HomeEffect.ShowMessage(R.string.generic_error))
            }
        }
    }

    private fun goBack() {
        if (state.value.isRecording) {
            updateState { copy(isRecording = false) }
            viewModelScope.launch { sendEffect(HomeEffect.SetRecording(false)) }
        }
        show(
            when (state.value.screen) {
                ArDrawScreen.ONBOARDING_LIGHTBOX -> ArDrawScreen.ONBOARDING_PROJECTOR
                ArDrawScreen.ONBOARDING_LESSONS -> ArDrawScreen.ONBOARDING_LIGHTBOX
                ArDrawScreen.ONBOARDING_TOPICS -> ArDrawScreen.ONBOARDING_LESSONS
                ArDrawScreen.ONBOARDING_PAYWALL -> state.value.paywallOrigin ?: state.value.screen
                ArDrawScreen.HOME_SOURCE_MODAL, ArDrawScreen.GALLERY,
                ArDrawScreen.SETTINGS -> ArDrawScreen.HOME

                ArDrawScreen.SETTINGS_DETAIL -> ArDrawScreen.SETTINGS
                ArDrawScreen.SEARCH -> state.value.searchOrigin
                ArDrawScreen.SEARCH_RESULTS -> ArDrawScreen.SEARCH
                ArDrawScreen.FILTER -> ArDrawScreen.GALLERY
                ArDrawScreen.LEARN_LEVEL_DETAIL -> ArDrawScreen.LEARN_PATH
                ArDrawScreen.LEARN_CATEGORY_DETAIL -> ArDrawScreen.LEARN_CATEGORIES
                ArDrawScreen.LEARN_PATH, ArDrawScreen.LEARN_CATEGORIES,
                ArDrawScreen.PROFILE_FAVORITE, ArDrawScreen.PROFILE_FAVORITE_EMPTY,
                ArDrawScreen.PROFILE_ALBUM, ArDrawScreen.PROFILE_ALBUM_EMPTY -> ArDrawScreen.HOME

                ArDrawScreen.TUTORIAL_CAMERA, ArDrawScreen.TUTORIAL_SCREEN -> state.value.tutorialOrigin
                ArDrawScreen.DRAWING_CANVAS, ArDrawScreen.DRAWING_CAMERA,
                ArDrawScreen.DRAWING_OPACITY -> if (state.value.drawingWithCamera) {
                    ArDrawScreen.TUTORIAL_CAMERA
                } else {
                    ArDrawScreen.TUTORIAL_SCREEN
                }

                ArDrawScreen.DRAWING_COMPLETE -> state.value.drawingCompleteOrigin
                    ?: if (state.value.drawingWithCamera) {
                        ArDrawScreen.TUTORIAL_CAMERA
                    } else {
                        ArDrawScreen.TUTORIAL_SCREEN
                    }

                else -> state.value.screen
            },
        )
    }

    private fun profileFavoriteScreen(ids: Set<String> = state.value.favoriteArtworkIds) =
        if (ids.isEmpty()) ArDrawScreen.PROFILE_FAVORITE_EMPTY else ArDrawScreen.PROFILE_FAVORITE

    private fun profileAlbumScreen() =
        if (state.value.drawings.isEmpty()) ArDrawScreen.PROFILE_ALBUM_EMPTY else ArDrawScreen.PROFILE_ALBUM

    private fun show(screen: ArDrawScreen) = updateState { copy(screen = screen) }

    private fun beginDrawingSession() {
        drawingSessionGeneration += 1
    }

    private companion object {
        const val MAX_TOPICS = 3
        const val PERSONALIZATION_DELAY_MILLIS = 1_500L
        const val DEFAULT_OPACITY = 0.4f
        const val WEB_IMAGE_SEARCH_URL =
            "https://www.google.com/search?tbm=isch&q=drawing+reference"
        val DRAWING_SCREENS = setOf(
            ArDrawScreen.DRAWING_CANVAS,
            ArDrawScreen.DRAWING_CAMERA,
            ArDrawScreen.DRAWING_OPACITY,
        )
    }
}
