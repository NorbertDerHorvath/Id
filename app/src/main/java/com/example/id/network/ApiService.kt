package com.example.id.network

import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/workday-events")
    suspend fun uploadWorkdayEvent(@Body workdayEvent: WorkdayEvent): Response<WorkdayEvent>

    @POST("api/loading-events")
    suspend fun uploadLoadingEvent(@Body loadingEvent: LoadingEvent): Response<LoadingEvent>

    @POST("api/refuel-events")
    suspend fun uploadRefuelEvent(@Body refuelEvent: RefuelEvent): Response<RefuelEvent>
}
