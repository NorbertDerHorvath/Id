package com.example.id.util

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    // Frissítve az új szerver URL-re
    private const val BASE_URL = "https://bok-server.onrender.com/"

    val instance: ApiService by lazy {
        // Logging Interceptor létrehozása
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // OkHttpClient létrehozása az interceptorral
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient) // Az egyedi kliens beállítása
            .build()

        retrofit.create(ApiService::class.java)
    }
}
