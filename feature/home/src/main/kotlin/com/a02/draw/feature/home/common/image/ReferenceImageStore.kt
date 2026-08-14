package com.a02.draw.feature.home.common.image

data class ImportedReference(
    val uri: String,
    val mimeType: String = "image/png",
    val displayName: String,
)

interface ReferenceImageStore {
    suspend fun importHttpsImage(url: String, filePrefix: String): ImportedReference
    suspend fun createEmojiComposite(emojis: List<String>): ImportedReference
    suspend fun copy(sourceUri: String, destinationUri: String)
}
