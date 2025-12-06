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
                if (event.serverId == null) { // New event, not yet on server
                    val response = apiService.postWorkday(event.copy(id = 0))
                    if (response.isSuccessful && response.body() != null) {
                        val serverEvent = response.body()!!
                        val updatedEvent = event.copy(
                            serverId = serverEvent.serverId,
                            isSynced = true
                        )
                        repository.updateWorkdayEvent(updatedEvent)
                    }
                } else { // Existing event, needs update on server
                    apiService.updateWorkday(event.serverId, event)
                }
            }

            // Refuel events (assuming they are only created, not updated)
            val unsyncedRefuelEvents = repository.getUnsyncedRefuelEvents()
            unsyncedRefuelEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postRefuel(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val serverEvent = response.body()!!
                    val updatedEvent = event.copy(
                        id = localId, // Keep local id
                        isSynced = true
                    )
                    repository.updateRefuelEvent(updatedEvent)
                }
            }

            // Loading events (assuming they are only created, not updated)
            val unsyncedLoadingEvents = repository.getUnsyncedLoadingEvents()
            unsyncedLoadingEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postLoading(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val serverEvent = response.body()!!
                    val updatedEvent = event.copy(
                        id = localId, // Keep local id
                        isSynced = true
                    )
                    repository.updateLoadingEvent(updatedEvent)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
