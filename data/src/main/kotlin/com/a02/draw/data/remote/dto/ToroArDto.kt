package com.a02.draw.data.remote.dto

data class EncryptedPayloadDto(val payload: String)

data class RemoteCategoryDto(
    val slug: String,
    val name: String,
    val count: Int = 0,
)

data class RemoteCategoriesEnvelopeDto(
    val data: List<RemoteCategoryDto> = emptyList(),
)

data class RemoteAssetDto(
    val assetId: String,
    val name: String,
    val itemNumber: Int = 0,
    val categorySlug: String,
    val categoryName: String,
    val subcategorySlug: String? = null,
    val subcategoryName: String? = null,
    val difficulty: String? = null,
    val imageUrl: String,
    val storageKey: String? = null,
)

data class RemoteAssetPageDto(
    val items: List<RemoteAssetDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 0,
)

data class RemoteAssetsEnvelopeDto(
    val data: RemoteAssetPageDto = RemoteAssetPageDto(),
)

data class RemoteLessonStepDto(
    val stepNumber: Int,
    val imageUrl: String,
    val storageKey: String,
)

data class RemoteLessonDto(
    val lessonId: String,
    val name: String,
    val categorySlug: String,
    val categoryName: String,
    val subcategorySlug: String,
    val totalSteps: Int = 0,
    val coverImageUrl: String,
    val steps: List<RemoteLessonStepDto> = emptyList(),
)

data class RemoteLessonPageDto(
    val items: List<RemoteLessonDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 0,
) {
    val isCompleteSnapshot: Boolean
        get() = page == 1 && totalPages <= 1 && items.size == total
}

data class RemoteLessonsEnvelopeDto(
    val data: RemoteLessonPageDto = RemoteLessonPageDto(),
)

data class RemoteAssetEnvelopeDto(
    val data: RemoteAssetDto,
)
