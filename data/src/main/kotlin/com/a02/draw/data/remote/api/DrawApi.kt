package com.a02.draw.data.remote.api

import com.a02.draw.data.remote.dto.DrawingDto
import com.a02.draw.data.remote.dto.RemoteAssetEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteAssetsEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteCategoriesEnvelopeDto
import com.a02.draw.data.remote.dto.RemoteLessonsEnvelopeDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DrawApi {
    @GET("api/v1/categories")
    suspend fun getCategories(): RemoteCategoriesEnvelopeDto

    @GET("api/v1/assets")
    suspend fun getAssets(
        @Query("category") category: String? = null,
        @Query("subcategory") subcategory: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): RemoteAssetsEnvelopeDto

    @GET("api/v1/lessons")
    suspend fun getLessons(
        @Query("category") category: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): RemoteLessonsEnvelopeDto

    @GET("api/v1/assets/{id}")
    suspend fun getAsset(@Path("id") id: String): RemoteAssetEnvelopeDto

    // Kept for the local Room networking example and its focused API test.
    @GET("drawings")
    suspend fun getDrawings(): List<DrawingDto>
}
