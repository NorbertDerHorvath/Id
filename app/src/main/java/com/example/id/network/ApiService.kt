package com.example.id.network

import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.User
import com.example.id.data.entities.WorkdayEvent
import retrofit2.Response
import retrofit2.http.*

// Data classes for Login
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val message: String, val username: String, val role: String, val permissions: List<String>)

// Data classes for Registration
data class RegisterRequest(val username: String, val password: String, val role: String, val companyName: String? = null, val adminEmail: String? = null)
data class RegisterResponse(val message: String, val userId: String, val username: String, val role: String)

data class ValidateRequest(val token: String)
data class ValidateResponse(val valid: Boolean)

// Admin User Management
data class AdminUserRequest(val username: String, val password: String, val role: String, val companyId: String? = null, val permissions: List<String>)
data class AdminUserUpdate(val password: String? = null, val role: String? = null, val permissions: List<String>? = null)

interface ApiService {
    // ... (existing workday and refuel endpoints)
    @GET("api/workday-events")
    suspend fun getWorkdayEvents(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("carPlate") carPlate: String? = null
    ): Response<List<WorkdayEvent>>

    @POST("api/workday-events")
    suspend fun postWorkday(@Body workday: WorkdayEvent): Response<WorkdayEvent>

    @PUT("api/workday-events/{id}")
    suspend fun updateWorkday(@Path("id") id: Long, @Body workday: WorkdayEvent): Response<WorkdayEvent>

    @DELETE("api/workday-events/{id}")
    suspend fun deleteWorkday(@Path("id") id: Long): Response<Void>

    @GET("api/refuel-events")
    suspend fun getRefuelEvents(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("carPlate") carPlate: String? = null
    ): Response<List<RefuelEvent>>

    @POST("api/refuel-events")
    suspend fun postRefuel(@Body refuel: RefuelEvent): Response<RefuelEvent>

    @PUT("api/refuel-events/{id}")
    suspend fun updateRefuel(@Path("id") id: Long, @Body refuel: RefuelEvent): Response<RefuelEvent>

    @DELETE("api/refuel-events/{id}")
    suspend fun deleteRefuel(@Path("id") id: Long): Response<Void>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @GET("api/validate-token")
    suspend fun validateToken(): Response<ValidateResponse>

    @DELETE("api/all-data")
    suspend fun deleteAllData(): Response<Void>

    // Admin routes
    @GET("api/admin/users")
    suspend fun getUsers(): Response<List<User>>

    @POST("api/admin/users")
    suspend fun createUser(@Body user: AdminUserRequest): Response<User>

    @PUT("api/admin/users/{userId}")
    suspend fun updateUser(@Path("userId") userId: String, @Body user: AdminUserUpdate): Response<User>
}
