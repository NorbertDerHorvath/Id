package com.example.id

import android.app.Application
import com.example.id.data.AppRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var repository: AppRepository
}
