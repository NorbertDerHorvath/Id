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
        if (token == null) {
            Log.d("SyncWorker", "Token is null, aborting sync.")
            return Result.failure() // No token, no sync
        }

        return try {
            // Workday events
            val unsyncedWorkdayEvents = repository.getUnsyncedWorkdayEvents()
            unsyncedWorkdayEvents.forEach { event ->
                val localId = event.id

                val response = if (event.isSynced) { // Should not happen, but as a safeguard
                    apiService.updateWorkday(event.id, event)
                } else {
                    apiService.postWorkday(event.copy(id = 0))
                }

                if (response.isSuccessful && response.body() != null) {
                    val syncedEvent = response.body()!!.copy(
                        isSynced = true
                    )
                    repository.replaceWorkdayEvent(localId, syncedEvent)
                } else {
                    Log.e("SyncWorker", "Workday sync failed for event $localId: ${response.code()} - ${response.message()}")
                }
            }

            // Refuel events
            val unsyncedRefuelEvents = repository.getUnsyncedRefuelEvents()
            unsyncedRefuelEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postRefuel(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val syncedEvent = response.body()!!.copy(
                        isSynced = true
                    )
                    repository.replaceRefuelEvent(localId, syncedEvent)
                } else {
                    Log.e("SyncWorker", "Refuel sync failed for event $localId: ${response.code()} - ${response.message()}")
                }
            }

            // Loading events
            val unsyncedLoadingEvents = repository.getUnsyncedLoadingEvents()
            unsyncedLoadingEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postLoading(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val syncedEvent = response.body()!!.copy(
                        isSynced = true
                    )
                    repository.replaceLoadingEvent(localId, syncedEvent)
                } else {
                    Log.e("SyncWorker", "Loading sync failed for event $localId: ${response.code()} - ${response.message()}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
