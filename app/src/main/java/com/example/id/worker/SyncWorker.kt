package com.example.id.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.id.data.AppRepository
import com.example.id.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AppRepository,
    private val apiService: ApiService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Workday events
            val unsyncedWorkdayEvents = repository.getUnsyncedWorkdayEvents()
            unsyncedWorkdayEvents.forEach { event ->
                val response = apiService.postWorkday(event)
                if (response.isSuccessful) {
                    repository.setWorkdayEventSynced(event.id)
                }
            }

            // Refuel events
            val unsyncedRefuelEvents = repository.getUnsyncedRefuelEvents()
            unsyncedRefuelEvents.forEach { event ->
                val response = apiService.postRefuel(event)
                if (response.isSuccessful) {
                    repository.setRefuelEventSynced(event.id)
                }
            }

            // Loading events
            val unsyncedLoadingEvents = repository.getUnsyncedLoadingEvents()
            unsyncedLoadingEvents.forEach { event ->
                val response = apiService.postLoading(event)
                if (response.isSuccessful) {
                    repository.setLoadingEventSynced(event.id)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
