package com.a02.draw.feature.home.common.model

enum class BottomDestination { HOME, LEARN, PROFILE, SETTINGS }

enum class DeviceImageSource { GALLERY, CAMERA }

enum class GalleryFilter {
    SAVED,
    ALL,
    EASY,
    JUJUTSU_KAISEN,
    ONE_PIECE,
    DORAEMON,
}

enum class DrawingMode { CAMERA, SCREEN }

enum class DrawingTool { OPACITY, CANVAS, CAMERA }

enum class DrawingCropRatio { RESET, SQUARE, PORTRAIT, LANDSCAPE }

enum class DrawingCanvasPanel { NONE, ADJUST, CROP, GRID }

enum class DrawingCameraPanel { NONE, ZOOM, FLASH, CAPTURE, RECORD, RATIO }

enum class DrawingCameraRatio { FULL, RATIO_16_9, RATIO_4_3, SQUARE }
