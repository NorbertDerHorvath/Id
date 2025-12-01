package com.example.id.di

import android.content.Context
import android.content.SharedPreferences
import com.example.id.PREFS_NAME
import com.example.id.util.ApiService
import com.example.id.util.NetworkClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideApiService(prefs: SharedPreferences): ApiService {
        return NetworkClient.create(prefs)
    }
}
