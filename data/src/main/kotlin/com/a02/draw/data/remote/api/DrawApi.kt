package com.a02.draw.data.remote.api

import com.a02.draw.data.remote.dto.DrawingDto
import com.a02.draw.data.remote.dto.EncryptedPayloadDto
import com.a02.draw.data.remote.dto.RemoteAssetEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteAssetsEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteCategoriesEnvelopeDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface DrawApi {
    suspend fun getCategories(): RemoteCategoriesEnvelopeDto

    suspend fun getAssets(
        rootFamily: String? = null,
        category: String? = null,
        subcategory: String? = null,
        search: String? = null,
        page: Int = 1,
        limit: Int = 20,
    ): RemoteAssetsEnvelopeDto

    suspend fun getAsset(id: String): RemoteAssetEnvelopeDto

    // Kept for the local Room networking example and its focused API test.
    suspend fun getDrawings(): List<DrawingDto>
}

interface ToroArApi {
    @Headers(AES_RESPONSE_HEADER)
    @GET("api/v1/categories")
    suspend fun getCategories(): EncryptedPayloadDto

    @Headers(AES_RESPONSE_HEADER)
    @GET("api/v1/assets")
    suspend fun getAssets(
        @Query("rootFamily") rootFamily: String? = null,
        @Query("category") category: String? = null,
        @Query("subcategory") subcategory: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): EncryptedPayloadDto

    @Headers(AES_RESPONSE_HEADER)
    @GET("api/v1/assets/{id}")
    suspend fun getAsset(@Path("id") id: String): EncryptedPayloadDto

    @GET("drawings")
    suspend fun getDrawings(): List<DrawingDto>

    private companion object {
        const val AES_RESPONSE_HEADER = "X-Enable-AES: true"
    }
}
