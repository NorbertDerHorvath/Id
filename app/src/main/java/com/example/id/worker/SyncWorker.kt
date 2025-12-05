package com.example.id.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.id.AUTH_TOKEN_KEY
import com.example.id.data.AppRepository
import com.example.id.data.entities.User
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
            // 1. Fetch all events from the server
            val workdayEventsResponse = apiService.getWorkdayEvents()
            val refuelEventsResponse = apiService.getRefuelEvents()
            val loadingEventsResponse = apiService.getLoadingEvents()

            if (workdayEventsResponse.isSuccessful && refuelEventsResponse.isSuccessful && loadingEventsResponse.isSuccessful) {
                val serverWorkdayEvents = workdayEventsResponse.body() ?: emptyList()
                val serverRefuelEvents = refuelEventsResponse.body() ?: emptyList()
                val serverLoadingEvents = loadingEventsResponse.body() ?: emptyList()

                // 2. Clear synced data from local database
                repository.clearSyncedData()

                // 3. Insert server data into local database
                repository.insertWorkdayEvents(serverWorkdayEvents.map { it.copy(isSynced = true, userId = it.user?.id ?: it.userId) })
                repository.insertRefuelEvents(serverRefuelEvents.map { it.copy(isSynced = true, userId = it.user?.id ?: it.userId) })
                repository.insertLoadingEvents(serverLoadingEvents.map { it.copy(isSynced = true, userId = it.user?.id ?: it.userId) })

            } else {
                Log.e("SyncWorker", "Failed to fetch data from server")
            }

            // 4. Sync unsynced local data
            val unsyncedWorkdayEvents = repository.getUnsyncedWorkdayEvents()
            unsyncedWorkdayEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postWorkday(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val syncedEvent = body.copy(isSynced = true, userId = body.user?.id ?: body.userId)
                    repository.replaceWorkdayEvent(localId, syncedEvent)
                } else {
                    Log.e("SyncWorker", "Workday sync failed for event $localId: ${response.code()} - ${response.message()}")
                }
            }

            val unsyncedRefuelEvents = repository.getUnsyncedRefuelEvents()
            unsyncedRefuelEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postRefuel(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val syncedEvent = body.copy(isSynced = true, userId = body.user?.id ?: body.userId)
                    repository.replaceRefuelEvent(localId, syncedEvent)
                } else {
                    Log.e("SyncWorker", "Refuel sync failed for event $localId: ${response.code()} - ${response.message()}")
                }
            }

            val unsyncedLoadingEvents = repository.getUnsyncedLoadingEvents()
            unsyncedLoadingEvents.forEach { event ->
                val localId = event.id
                val response = apiService.postLoading(event.copy(id = 0))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val syncedEvent = body.copy(isSynced = true, userId = body.user?.id ?: body.userId)
                    repository.replaceLoadingEvent(localId, syncedEvent)
                } else {
                    Log.e("SyncWorker", "Loading sync failed for event $localId: ${response.code()} - ${response.message()}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed (Ask Gemini)", e)
            Result.retry()
        }
    }
}
