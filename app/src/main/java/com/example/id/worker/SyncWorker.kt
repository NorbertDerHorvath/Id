package com.example.id.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.id.AUTH_TOKEN_KEY
import com.example.id.data.AppRepository
import com.example.id.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AppRepository,
    private val apiService: ApiService,
    private val prefs: SharedPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val token = prefs.getString(AUTH_TOKEN_KEY, null)
        Log.d("SyncWorker", "Token: $token")

        return try {
            // Workday events
            val unsyncedWorkdayEvents = repository.getUnsyncedWorkdayEvents()
            unsyncedWorkdayEvents.forEach { event ->
                val response = apiService.postWorkday(event)
                if (response.isSuccessful) {
                    repository.setWorkdayEventSynced(event.id)
                } else {
                    Log.e("SyncWorker", "Workday sync failed: ${response.code()} - ${response.message()}")
                }
            }

            // Refuel events
            val unsyncedRefuelEvents = repository.getUnsyncedRefuelEvents()
            unsyncedRefuelEvents.forEach { event ->
                val response = apiService.postRefuel(event)
                if (response.isSuccessful) {
                    repository.setRefuelEventSynced(event.id)
                } else {
                    Log.e("SyncWorker", "Refuel sync failed: ${response.code()} - ${response.message()}")
                }
            }

            // Loading events
            val unsyncedLoadingEvents = repository.getUnsyncedLoadingEvents()
            unsyncedLoadingEvents.forEach { event ->
                val response = apiService.postLoading(event)
                if (response.isSuccessful) {
                    repository.setLoadingEventSynced(event.id)
                } else {
                    Log.e("SyncWorker", "Loading sync failed: ${response.code()} - ${response.message()}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
