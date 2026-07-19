package com.a02.draw.data.remote.api

import com.a02.draw.data.remote.dto.ArCatalogDto
import com.a02.draw.data.remote.dto.DrawingDto
import retrofit2.http.GET

interface DrawApi {
    @GET("v1/catalog")
    suspend fun getCatalog(): ArCatalogDto

    @GET("drawings")
    suspend fun getDrawings(): List<DrawingDto>
}
