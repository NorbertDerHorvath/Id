package com.example.id.util

import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Data classes for Login
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val message: String,
    val username: String
)

data class ValidateRequest(
    val token: String
)

data class ValidateResponse(
    val valid: Boolean
)

interface ApiService {
    @POST("api/workday-events")
    suspend fun postWorkday(@Body workday: WorkdayEvent): Response<Void>

    @POST("api/refuel-events")
    suspend fun postRefuel(@Body refuel: RefuelEvent): Response<Void>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/validate-token")
    suspend fun validateToken(@Body request: ValidateRequest): Response<ValidateResponse>
}
