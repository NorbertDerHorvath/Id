package com.example.id.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.network.ApiService
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_EVENT_TYPE = "EVENT_TYPE"
        const val KEY_EVENT_JSON = "EVENT_JSON"

        const val TYPE_WORKDAY = "WORKDAY"
        const val TYPE_REFUEL = "REFUEL"
        const val TYPE_LOADING = "LOADING"
    }

    override suspend fun doWork(): Result {
        val eventType = inputData.getString(KEY_EVENT_TYPE) ?: return Result.failure()
        val eventJson = inputData.getString(KEY_EVENT_JSON) ?: return Result.failure()

        return try {
            val response = when (eventType) {
                TYPE_WORKDAY -> {
                    val event = Gson().fromJson(eventJson, WorkdayEvent::class.java)
                    apiService.uploadWorkdayEvent(event)
                }
                TYPE_REFUEL -> {
                    val event = Gson().fromJson(eventJson, RefuelEvent::class.java)
                    apiService.uploadRefuelEvent(event)
                }
                TYPE_LOADING -> {
                    val event = Gson().fromJson(eventJson, LoadingEvent::class.java)
                    apiService.uploadLoadingEvent(event)
                }
                else -> return Result.failure()
            }

            if (response.isSuccessful) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}