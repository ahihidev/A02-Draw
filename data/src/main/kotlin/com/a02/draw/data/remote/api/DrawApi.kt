package com.a02.draw.data.remote.api

import com.a02.draw.data.remote.dto.DrawingDto
import retrofit2.http.GET

interface DrawApi {
    @GET("drawings")
    suspend fun getDrawings(): List<DrawingDto>
}
