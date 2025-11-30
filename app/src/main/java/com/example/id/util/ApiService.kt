package com.example.id.util

import com.example.id.data.entities.WorkdayEvent
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/workday-events")
    suspend fun postWorkday(@Body workday: WorkdayEvent): retrofit2.Response<Void>
}
