package com.a02.draw.feature.home.common.drawing

import android.graphics.Color
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.model.DrawingCameraPanel
import com.a02.draw.feature.home.common.model.DrawingCameraRatio
import com.a02.draw.feature.home.common.model.DrawingCanvasPanel
import com.a02.draw.feature.home.common.model.DrawingCropRatio
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.motion.HomeMotion
import com.a02.draw.feature.home.common.motion.pulse
import com.a02.draw.feature.home.common.motion.slideFadeVisible
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.databinding.ScreenDrawingControlsBinding

sealed interface DrawingControlAction {
    data object Back : DrawingControlAction
    data object Complete : DrawingControlAction
    data object PreviousStep : DrawingControlAction
    data object NextStep : DrawingControlAction
    data class SelectTool(val tool: DrawingTool) : DrawingControlAction
    data object ToggleOverlay : DrawingControlAction
    data object ToggleLock : DrawingControlAction
    data object ToggleFlip : DrawingControlAction
    data object RotateOverlay : DrawingControlAction
    data object CenterOverlay : DrawingControlAction
    data object ResetOverlayTransform : DrawingControlAction
    data object ToggleRemoveBackground : DrawingControlAction
    data class OpenCanvasPanel(val panel: DrawingCanvasPanel) : DrawingControlAction
    data class SelectCropRatio(val ratio: DrawingCropRatio) : DrawingControlAction
    data class SelectGridSize(val size: Int) : DrawingControlAction
    data class OpenCameraPanel(val panel: DrawingCameraPanel) : DrawingControlAction
    data class SelectCameraZoom(val zoom: Float) : DrawingControlAction
    data class SelectCameraRatio(val ratio: DrawingCameraRatio) : DrawingControlAction
    data class SelectCaptureDelay(val seconds: Int) : DrawingControlAction
    data object ToggleCameraLens : DrawingControlAction
    data object ToggleCameraGuide : DrawingControlAction
    data object Shutter : DrawingControlAction
    data class ChangeOpacity(val opacity: Float) : DrawingControlAction
}

class DrawingControlsBinder(
    private val binding: ScreenDrawingControlsBinding,
    private val onAction: (DrawingControlAction) -> Unit,
) {
    private var rendering = false
    private var previousTool: DrawingTool? = null
    private var previousCanvasPanel: DrawingCanvasPanel? = null
    private var previousCameraPanel: DrawingCameraPanel? = null

    init {
        binding.backButton.setDebouncedClickListener { onAction(DrawingControlAction.Back) }
        binding.completeButton.setDebouncedClickListener { onAction(DrawingControlAction.Complete) }
        binding.previousStep.setOnClickListener { onAction(DrawingControlAction.PreviousStep) }
        binding.nextStep.setDebouncedClickListener { onAction(DrawingControlAction.NextStep) }
        binding.toolOpacity.setDebouncedClickListener {
            onAction(DrawingControlAction.SelectTool(DrawingTool.OPACITY))
        }
        binding.toolCanvas.setDebouncedClickListener {
            onAction(DrawingControlAction.SelectTool(DrawingTool.CANVAS))
        }
        binding.toolCamera.setDebouncedClickListener {
            onAction(DrawingControlAction.SelectTool(DrawingTool.CAMERA))
        }
        binding.toolHide.setOnClickListener { onAction(DrawingControlAction.ToggleOverlay) }
        binding.actionLock.setOnClickListener { onAction(DrawingControlAction.ToggleLock) }
        binding.actionAdjust.setOnClickListener {
            onAction(DrawingControlAction.OpenCanvasPanel(DrawingCanvasPanel.ADJUST))
        }
        binding.actionRemoveBg.setOnClickListener {
            onAction(DrawingControlAction.ToggleRemoveBackground)
        }
        binding.actionCrop.setOnClickListener {
            onAction(DrawingControlAction.OpenCanvasPanel(DrawingCanvasPanel.CROP))
        }
        binding.actionGrid.setOnClickListener {
            onAction(DrawingControlAction.OpenCanvasPanel(DrawingCanvasPanel.GRID))
        }
        binding.actionZoom.setOnClickListener {
            onAction(DrawingControlAction.OpenCameraPanel(DrawingCameraPanel.ZOOM))
        }
        binding.actionFlash.setOnClickListener {
            onAction(DrawingControlAction.OpenCameraPanel(DrawingCameraPanel.FLASH))
        }
        binding.actionCapture.setOnClickListener {
            onAction(DrawingControlAction.OpenCameraPanel(DrawingCameraPanel.CAPTURE))
        }
        binding.actionRecord.setOnClickListener {
            onAction(DrawingControlAction.OpenCameraPanel(DrawingCameraPanel.RECORD))
        }
        binding.actionRatio.setOnClickListener {
            onAction(DrawingControlAction.OpenCameraPanel(DrawingCameraPanel.RATIO))
        }
        binding.optionReset.setOnClickListener {
            onAction(DrawingControlAction.SelectCropRatio(DrawingCropRatio.RESET))
        }
        binding.optionSquare.setOnClickListener {
            onAction(DrawingControlAction.SelectCropRatio(DrawingCropRatio.SQUARE))
        }
        binding.optionPortrait.setOnClickListener {
            onAction(DrawingControlAction.SelectCropRatio(DrawingCropRatio.PORTRAIT))
        }
        binding.optionLandscape.setOnClickListener {
            onAction(DrawingControlAction.SelectCropRatio(DrawingCropRatio.LANDSCAPE))
        }
        binding.optionGrid3.setOnClickListener { onAction(DrawingControlAction.SelectGridSize(3)) }
        binding.optionGrid4.setOnClickListener { onAction(DrawingControlAction.SelectGridSize(4)) }
        binding.optionGrid5.setOnClickListener { onAction(DrawingControlAction.SelectGridSize(5)) }
        binding.optionAdjustFlip.setOnClickListener { onAction(DrawingControlAction.ToggleFlip) }
        binding.optionAdjustRotate.setOnClickListener { onAction(DrawingControlAction.RotateOverlay) }
        binding.optionAdjustCenter.setOnClickListener { onAction(DrawingControlAction.CenterOverlay) }
        binding.optionAdjustReset.setOnClickListener {
            onAction(DrawingControlAction.ResetOverlayTransform)
        }
        binding.optionZoomHalf.setOnClickListener {
            onAction(DrawingControlAction.SelectCameraZoom(0.5f))
        }
        binding.optionZoomOneHalf.setOnClickListener {
            onAction(DrawingControlAction.SelectCameraZoom(1.5f))
        }
        binding.optionRatioFull.setOnClickListener {
            onAction(DrawingControlAction.SelectCameraRatio(DrawingCameraRatio.FULL))
        }
        binding.optionRatio169.setOnClickListener {
            onAction(DrawingControlAction.SelectCameraRatio(DrawingCameraRatio.RATIO_16_9))
        }
        binding.optionRatio43.setOnClickListener {
            onAction(DrawingControlAction.SelectCameraRatio(DrawingCameraRatio.RATIO_4_3))
        }
        binding.optionRatioSquare.setOnClickListener {
            onAction(DrawingControlAction.SelectCameraRatio(DrawingCameraRatio.SQUARE))
        }
        binding.optionTimerOff.setOnClickListener {
            onAction(DrawingControlAction.SelectCaptureDelay(0))
        }
        binding.optionTimer3.setOnClickListener {
            onAction(DrawingControlAction.SelectCaptureDelay(3))
        }
        binding.optionTimer10.setOnClickListener {
            onAction(DrawingControlAction.SelectCaptureDelay(10))
        }
        binding.optionSwitchCamera.setOnClickListener {
            if (HomeMotion.enabled()) {
                binding.optionSwitchCamera.animate().cancel()
                binding.optionSwitchCamera.animate().rotationBy(180f)
                    .setDuration(HomeMotion.CONTENT).start()
            }
            onAction(DrawingControlAction.ToggleCameraLens)
        }
        binding.optionCameraGuide.setOnClickListener {
            onAction(DrawingControlAction.ToggleCameraGuide)
        }
        binding.shutterButton.setDebouncedClickListener {
            binding.shutterButton.pulse(1.1f)
            onAction(DrawingControlAction.Shutter)
        }
        binding.opacitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !rendering) {
                    onAction(DrawingControlAction.ChangeOpacity(progress / 100f))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    fun render(state: DrawingSession) {
        val opacityMode = state.tool == DrawingTool.OPACITY
        val canvasMode = state.tool == DrawingTool.CANVAS
        val cameraMode = state.tool == DrawingTool.CAMERA && state.mode == DrawingMode.CAMERA
        binding.opacityPanel.slideFadeVisible(opacityMode)
        if (!binding.opacitySlider.isPressed) {
            rendering = true
            binding.opacitySlider.progress = (state.opacity * 100).toInt()
            rendering = false
        }
        binding.toolOpacityIcon.setImageResource(
            if (opacityMode) R.drawable.icon_tool_opacity_selected else R.drawable.icon_tool_opacity_unselected,
        )
        binding.toolCanvasIcon.setImageResource(
            if (canvasMode) R.drawable.icon_tool_canvas_selected else R.drawable.icon_tool_canvas_unselected,
        )
        binding.toolCameraIcon.setImageResource(
            if (cameraMode) R.drawable.icon_tool_camera_selected else R.drawable.icon_tool_camera_unselected,
        )
        binding.toolCamera.isEnabled = state.mode == DrawingMode.CAMERA
        binding.toolCamera.alpha = if (binding.toolCamera.isEnabled) 1f else 0.4f
        if (previousTool != state.tool) {
            when (state.tool) {
                DrawingTool.OPACITY -> binding.toolOpacityIcon
                DrawingTool.CANVAS -> binding.toolCanvasIcon
                DrawingTool.CAMERA -> binding.toolCameraIcon
            }.pulse(1.12f)
        }
        binding.toolHideLabel.setText(if (state.overlayVisible) R.string.hide else R.string.show)
        binding.actionLock.renderSelected(state.overlayLocked)
        binding.actionAdjust.renderSelected(state.canvasPanel == DrawingCanvasPanel.ADJUST)
        binding.actionRemoveBg.renderSelected(state.removeImageEnabled)
        binding.actionCrop.renderSelected(state.canvasPanel == DrawingCanvasPanel.CROP)
        binding.actionGrid.renderSelected(state.canvasPanel == DrawingCanvasPanel.GRID)
        binding.actionZoom.renderSelected(state.cameraPanel == DrawingCameraPanel.ZOOM)
        binding.actionFlash.renderSelected(state.flashEnabled)
        binding.actionCapture.renderSelected(state.cameraPanel == DrawingCameraPanel.CAPTURE)
        binding.actionRecord.renderSelected(state.cameraPanel == DrawingCameraPanel.RECORD)
        binding.actionRatio.renderSelected(state.cameraPanel == DrawingCameraPanel.RATIO)
        listOf(
            binding.actionLock,
            binding.actionAdjust,
            binding.actionRemoveBg,
            binding.actionCrop,
            binding.actionGrid,
        ).forEach { it.isVisible = canvasMode }
        listOf(
            binding.actionZoom,
            binding.actionFlash,
            binding.actionCapture,
            binding.actionRecord,
            binding.actionRatio,
        ).forEach { it.isVisible = cameraMode }
        val quickActionsVisible = canvasMode || cameraMode
        if (previousTool != null && previousTool != state.tool && quickActionsVisible &&
            binding.quickActionsScroll.isVisible
        ) {
            binding.quickActionsScroll.alpha = 0f
            binding.quickActionsScroll.translationY =
                binding.quickActionsScroll.resources.displayMetrics.density * 12f
        }
        binding.quickActionsScroll.slideFadeVisible(quickActionsVisible)
        renderOptions(state, canvasMode, cameraMode)
        binding.shutterButton.isVisible = cameraMode && state.cameraPanel in setOf(
            DrawingCameraPanel.CAPTURE,
            DrawingCameraPanel.RECORD,
        )
        binding.shutterButton.isSelected = state.cameraPanel == DrawingCameraPanel.RECORD
        binding.lessonNavigator.isVisible = state.lessonStepCount > 0
        if (state.lessonStepCount > 0) {
            binding.lessonTitle.text = state.referenceTitle
            binding.lessonStep.text = binding.root.context.getString(
                R.string.lesson_step_of,
                state.lessonStepIndex + 1,
                state.lessonStepCount,
            )
            binding.previousStep.isEnabled = state.lessonStepIndex > 0
            binding.previousStep.alpha = if (binding.previousStep.isEnabled) 1f else 0.45f
            val isLastStep = state.lessonStepIndex == state.lessonStepCount - 1
            binding.nextStep.setText(
                if (isLastStep) R.string.done else R.string.next,
            )
            binding.nextStep.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                if (isLastStep) R.drawable.ic_lesson_done else R.drawable.ic_lesson_next,
                0,
            )
        }
        previousTool = state.tool
        previousCanvasPanel = state.canvasPanel
        previousCameraPanel = state.cameraPanel
    }

    private fun renderOptions(
        state: DrawingSession,
        canvasMode: Boolean,
        cameraMode: Boolean,
    ) {
        val adjustVisible = canvasMode && state.canvasPanel == DrawingCanvasPanel.ADJUST
        val cropVisible = canvasMode && state.canvasPanel == DrawingCanvasPanel.CROP
        val gridVisible = canvasMode && state.canvasPanel == DrawingCanvasPanel.GRID
        val zoomVisible = cameraMode && state.cameraPanel == DrawingCameraPanel.ZOOM
        val ratioVisible = cameraMode && state.cameraPanel == DrawingCameraPanel.RATIO
        val captureVisible = cameraMode && state.cameraPanel == DrawingCameraPanel.CAPTURE
        val optionsVisible =
            adjustVisible || cropVisible || gridVisible || zoomVisible || ratioVisible || captureVisible
        val panelChanged = previousCanvasPanel != state.canvasPanel ||
                previousCameraPanel != state.cameraPanel
        if (panelChanged && optionsVisible && binding.optionsScroll.isVisible) {
            binding.optionsScroll.alpha = 0f
            binding.optionsScroll.translationY =
                binding.optionsScroll.resources.displayMetrics.density * 12f
        }
        binding.optionsScroll.slideFadeVisible(optionsVisible)
        listOf(
            binding.optionAdjustFlip,
            binding.optionAdjustRotate,
            binding.optionAdjustCenter,
            binding.optionAdjustReset,
        ).forEach { it.isVisible = adjustVisible }
        listOf(
            binding.optionReset,
            binding.optionSquare,
            binding.optionPortrait,
            binding.optionLandscape
        )
            .forEach { it.isVisible = cropVisible }
        listOf(binding.optionGrid3, binding.optionGrid4, binding.optionGrid5)
            .forEach { it.isVisible = gridVisible }
        listOf(binding.optionZoomHalf, binding.optionZoomOneHalf)
            .forEach { it.isVisible = zoomVisible }
        listOf(
            binding.optionRatioFull,
            binding.optionRatio169,
            binding.optionRatio43,
            binding.optionRatioSquare
        )
            .forEach { it.isVisible = ratioVisible }
        listOf(
            binding.optionTimerOff,
            binding.optionTimer3,
            binding.optionTimer10,
            binding.optionSwitchCamera,
            binding.optionCameraGuide,
        ).forEach { it.isVisible = captureVisible }
        binding.optionAdjustFlip.renderSelected(state.overlayFlipped)
        binding.optionAdjustRotate.renderSelected(state.overlayRotationDegrees != 0f)
        // Center and Reset are one-shot commands, not persistent modes.
        binding.optionAdjustCenter.renderSelected(false)
        binding.optionAdjustReset.renderSelected(false)
        binding.optionReset.renderSelected(state.cropRatio == DrawingCropRatio.RESET)
        binding.optionSquare.renderSelected(state.cropRatio == DrawingCropRatio.SQUARE)
        binding.optionPortrait.renderSelected(state.cropRatio == DrawingCropRatio.PORTRAIT)
        binding.optionLandscape.renderSelected(state.cropRatio == DrawingCropRatio.LANDSCAPE)
        binding.optionGrid3.renderSelected(state.gridSize == 3)
        binding.optionGrid4.renderSelected(state.gridSize == 4)
        binding.optionGrid5.renderSelected(state.gridSize == 5)
        binding.optionZoomHalf.renderSelected(state.cameraZoom == 0.5f)
        binding.optionZoomOneHalf.renderSelected(state.cameraZoom == 1.5f)
        binding.optionRatioFull.renderSelected(state.cameraRatio == DrawingCameraRatio.FULL)
        binding.optionRatio169.renderSelected(state.cameraRatio == DrawingCameraRatio.RATIO_16_9)
        binding.optionRatio43.renderSelected(state.cameraRatio == DrawingCameraRatio.RATIO_4_3)
        binding.optionRatioSquare.renderSelected(state.cameraRatio == DrawingCameraRatio.SQUARE)
        binding.optionTimerOff.renderSelected(state.captureDelaySeconds == 0)
        binding.optionTimer3.renderSelected(state.captureDelaySeconds == 3)
        binding.optionTimer10.renderSelected(state.captureDelaySeconds == 10)
        binding.optionSwitchCamera.setText(
            if (state.useFrontCamera) R.string.back_camera else R.string.front_camera,
        )
        binding.optionSwitchCamera.renderSelected(state.useFrontCamera)
        binding.optionCameraGuide.renderSelected(state.cameraGuideEnabled)
    }

    private fun TextView.renderSelected(selected: Boolean) {
        val changedToSelected = !isSelected && selected
        isSelected = selected
        setTextColor(Color.WHITE)
        if (changedToSelected) pulse(1.06f)
    }
}
