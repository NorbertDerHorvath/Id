package com.example.id.repository

import android.content.SharedPreferences
import com.example.id.AUTH_TOKEN_KEY
import com.example.id.network.ApiService
import com.example.id.network.LoginRequest
import com.example.id.network.LoginResponse
import com.example.id.network.RegisterRequest
import com.example.id.network.RegisterResponse
import com.example.id.network.ValidateResponse
import retrofit2.Response
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val prefs: SharedPreferences
) {

    suspend fun login(username: String, password: String): Response<LoginResponse> {
        return apiService.login(LoginRequest(username, password))
    }

    suspend fun register(username: String, password: String, role: String, companyName: String?, adminEmail: String?): Response<RegisterResponse> {
        return apiService.register(RegisterRequest(username, password, role, companyName, adminEmail))
    }

    suspend fun validateToken(): Response<ValidateResponse> {
        return apiService.validateToken()
    }

    fun saveToken(token: String) {
        prefs.edit().putString(AUTH_TOKEN_KEY, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(AUTH_TOKEN_KEY, null)
    }

    fun clearToken() {
        prefs.edit().remove(AUTH_TOKEN_KEY).apply()
    }
}
