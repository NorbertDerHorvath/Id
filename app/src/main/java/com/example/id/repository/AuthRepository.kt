package com.example.id.repository

import android.content.SharedPreferences
import com.example.id.AUTH_TOKEN_KEY
import com.example.id.util.ApiService
import com.example.id.util.LoginRequest
import com.example.id.util.LoginResponse
import com.example.id.util.ValidateRequest
import com.example.id.util.ValidateResponse
import retrofit2.Response
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val prefs: SharedPreferences
) {

    suspend fun login(username: String, password: String): Response<LoginResponse> {
        return apiService.login(LoginRequest(username, password))
    }

    suspend fun validateToken(token: String): Response<ValidateResponse> {
        return apiService.validateToken(ValidateRequest(token))
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
