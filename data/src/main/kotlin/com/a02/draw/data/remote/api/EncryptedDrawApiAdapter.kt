package com.a02.draw.data.remote.api

import com.a02.draw.data.remote.crypto.AesPayloadDecryptor
import com.a02.draw.data.remote.dto.DrawingDto
import com.a02.draw.data.remote.dto.RemoteAssetEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteAssetsEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteCategoriesEnvelopeDto

internal class EncryptedDrawApiAdapter(
    private val api: ToroArApi,
    private val decryptor: AesPayloadDecryptor,
) : DrawApi {
    override suspend fun getCategories(): RemoteCategoriesEnvelopeDto =
        decryptor.decrypt(
            api.getCategories().payload,
            RemoteCategoriesEnvelopeDto::class.java,
        )

    override suspend fun getAssets(
        rootFamily: String?,
        category: String?,
        subcategory: String?,
        search: String?,
        page: Int,
        limit: Int,
    ): RemoteAssetsEnvelopeDto = decryptor.decrypt(
        api.getAssets(rootFamily, category, subcategory, search, page, limit).payload,
        RemoteAssetsEnvelopeDto::class.java,
    )

    override suspend fun getAsset(id: String): RemoteAssetEnvelopeDto =
        decryptor.decrypt(
            api.getAsset(id).payload,
            RemoteAssetEnvelopeDto::class.java,
        )

    override suspend fun getDrawings(): List<DrawingDto> = api.getDrawings()
}
