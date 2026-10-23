package com.a02.draw.feature.home.screen.emojimix

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.feature.home.common.image.ImportedReference
import com.a02.draw.feature.home.common.image.ReferenceImageStore
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class EmojiMixResultViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageStore: ReferenceImageStore,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<EmojiMixResultUiState, EmojiMixResultEffect>(
    initialState(savedStateHandle),
) {
    init {
        if (state.value.result == null) generate()
    }

    fun onAction(action: EmojiMixResultAction) {
        when (action) {
            EmojiMixResultAction.Retry -> generate()
            EmojiMixResultAction.Draw -> draw()
            EmojiMixResultAction.CreateNew -> send(EmojiMixResultEffect.CreateNew(state.value.mode))
            EmojiMixResultAction.Back -> send(EmojiMixResultEffect.NavigateBack)
            is EmojiMixResultAction.Save -> save(action.destinationUri)
        }
    }

    private fun generate() {
        val inputs = state.value.inputs
        if (inputs.size != state.value.mode.slotCount) {
            updateState { copy(isLoading = false, errorMessage = "Invalid emoji selection") }
            return
        }
        updateState { copy(isLoading = true, result = null, errorMessage = null) }
        launchCatching(
            onError = { throwable ->
                updateState {
                    copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Could not create emoji"
                    )
                }
            },
        ) {
            val imported = when (state.value.mode) {
                EmojiMixMode.MIX_2 -> {
                    val pair = requireNotNull(EmojiKitchenCatalog.find(inputs[0], inputs[1])) {
                        "This emoji pair is not available"
                    }
                    imageStore.importHttpsImage(pair.resultUrl, "emoji-mix-2")
                }

                EmojiMixMode.MIX_3 -> imageStore.createEmojiComposite(inputs)
            }
            val result = imported.toResult(state.value.mode, inputs)
            savedStateHandle[KEY_RESULT_URI] = result.uri
            savedStateHandle[KEY_RESULT_NAME] = result.displayName
            updateState { copy(isLoading = false, result = result) }
        }
    }

    private fun save(destinationUri: String) {
        val source = state.value.result?.uri ?: return
        launchCatching(
            onError = {
                sendEffect(
                    EmojiMixResultEffect.ShowError(
                        it.message ?: "Could not save image"
                    )
                )
            },
        ) {
            imageStore.copy(source, destinationUri)
            sendEffect(EmojiMixResultEffect.Saved)
        }
    }

    private fun draw() {
        val result = state.value.result ?: return
        drawingSession.reset(
            DrawingSession(
                referenceTitle = "Emoji Mix",
                pickedImageUri = result.uri,
                mode = DrawingMode.CAMERA,
            ),
        )
        send(EmojiMixResultEffect.NavigateToDrawingMode)
    }

    private fun send(effect: EmojiMixResultEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }

    private fun ImportedReference.toResult(mode: EmojiMixMode, inputs: List<String>) =
        EmojiMixResult(mode, inputs, uri, displayName)

    companion object {
        private const val KEY_RESULT_URI = "emoji_result.uri"
        private const val KEY_RESULT_NAME = "emoji_result.name"

        private fun initialState(handle: SavedStateHandle): EmojiMixResultUiState {
            val mode = handle.get<String>(EmojiMixHomeFragment.ARG_MODE)
                ?.let { runCatching { EmojiMixMode.valueOf(it) }.getOrNull() }
                ?: EmojiMixMode.MIX_2
            val inputs =
                handle.get<Array<String>>(EmojiMixPickerFragment.ARG_INPUTS).orEmpty().toList()
            val uri = handle.get<String>(KEY_RESULT_URI)
            val name = handle.get<String>(KEY_RESULT_NAME)
            val result =
                if (uri != null && name != null) EmojiMixResult(mode, inputs, uri, name) else null
            return EmojiMixResultUiState(
                mode = mode,
                inputs = inputs,
                isLoading = result == null,
                result = result,
            )
        }
    }
}
