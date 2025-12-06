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
            // UPLOAD UNSYNCED CHANGES
            // Workday events
            val unsyncedWorkdayEvents = repository.getUnsyncedWorkdayEvents()
            unsyncedWorkdayEvents.forEach { event ->
                if (event.endTime != null) { // Only sync finished workday events
                    if (event.id == null) { // New event, POST to server
                        val response = apiService.postWorkday(event)
                        if (response.isSuccessful && response.body() != null) {
                            val serverEvent = response.body()!!
                            val updatedEvent = event.copy(
                                id = serverEvent.id, // Update with server ID
                                isSynced = true
                            )
                            repository.updateWorkdayEvent(updatedEvent)
                        }
                    } else { // Existing event, PUT update to server
                        val response = apiService.updateWorkday(event.id, event)
                        if (response.isSuccessful) {
                            repository.updateWorkdayEvent(event.copy(isSynced = true))
                        }
                    }
                }
            }

            // Refuel events
            val unsyncedRefuelEvents = repository.getUnsyncedRefuelEvents()
            unsyncedRefuelEvents.forEach { event ->
                if (event.id == null) { // New event, POST to server
                    val response = apiService.postRefuel(event)
                    if (response.isSuccessful && response.body() != null) {
                        val serverEvent = response.body()!!
                        val updatedEvent = event.copy(
                            id = serverEvent.id,
                            isSynced = true
                        )
                        repository.updateRefuelEvent(updatedEvent)
                    }
                } else { // Existing event, PUT update to server
                    val response = apiService.updateRefuel(event.id, event)
                     if (response.isSuccessful) {
                        repository.updateRefuelEvent(event.copy(isSynced = true))
                    }
                }
            }

            // Loading events
            val unsyncedLoadingEvents = repository.getUnsyncedLoadingEvents()
            unsyncedLoadingEvents.forEach { event ->
                 if (event.endTime != null) { // Only sync finished loading events
                    if (event.id == null) { // New event, POST to server
                        val response = apiService.postLoading(event)
                        if (response.isSuccessful && response.body() != null) {
                            val serverEvent = response.body()!!
                            val updatedEvent = event.copy(
                                id = serverEvent.id,
                                isSynced = true
                            )
                            repository.updateLoadingEvent(updatedEvent)
                        }
                    } else { // Existing event, PUT update to server
                        val response = apiService.updateLoading(event.id, event)
                        if (response.isSuccessful) {
                            repository.updateLoadingEvent(event.copy(isSynced = true))
                        }
                    }
                }
            }

            // DOWNLOAD CHANGES FROM SERVER
            val serverWorkdays = apiService.getWorkdayEvents()
            if (serverWorkdays.isSuccessful) {
                repository.syncWorkdayEvents(serverWorkdays.body() ?: emptyList())
            }

            val serverRefuels = apiService.getRefuelEvents()
            if (serverRefuels.isSuccessful) {
                repository.syncRefuelEvents(serverRefuels.body() ?: emptyList())
            }

            val serverLoadings = apiService.getLoadingEvents()
            if (serverLoadings.isSuccessful) {
                repository.syncLoadingEvents(serverLoadings.body() ?: emptyList())
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
