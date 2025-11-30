package com.example.id.repository

import android.content.SharedPreferences
import com.example.id.util.ApiService
import com.example.id.util.LoginRequest
import com.example.id.util.LoginResponse
import retrofit2.Response
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val prefs: SharedPreferences
) {

    suspend fun login(username: String, password: String): Response<LoginResponse> {
        return apiService.login(LoginRequest(username, password))
    }

    fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }
}
