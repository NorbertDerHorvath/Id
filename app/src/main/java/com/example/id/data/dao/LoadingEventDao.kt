package com.example.id.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.id.data.entities.LoadingEvent
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface LoadingEventDao {
    @Insert
    suspend fun insertLoadingEvent(event: LoadingEvent): Long

    @Update
    suspend fun updateLoadingEvent(event: LoadingEvent)

    @Query("SELECT * FROM loading_events WHERE userId = :userId ORDER BY startTime DESC")
    fun getAllLoadingEvents(userId: String): Flow<List<LoadingEvent>>

    @Query("SELECT * FROM loading_events WHERE userId = :userId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getActiveLoadingEvent(userId: String): Flow<LoadingEvent?>

    @Query("SELECT * FROM loading_events WHERE workdayEventId = :workdayEventId ORDER BY startTime ASC")
    fun getLoadingsForWorkday(workdayEventId: Long): Flow<List<LoadingEvent>>

    @Query("""
        SELECT * FROM loading_events
        WHERE userId = :userId
        AND (:startDate IS NULL OR startTime >= :startDate)
        AND (:endDate IS NULL OR startTime < :endDate)
        ORDER BY startTime DESC
        """)
    fun getLoadingEventsForReport(userId: String, startDate: Date?, endDate: Date?): Flow<List<LoadingEvent>>

    @Query("SELECT * FROM loading_events WHERE id = :id")
    suspend fun getLoadingEventById(id: Long): LoadingEvent?

    @Query("DELETE FROM loading_events WHERE id = :id")
    suspend fun deleteLoadingEventById(id: Long)

    @Query("SELECT * FROM loading_events WHERE userId = :userId AND startTime >= :sevenDaysAgo ORDER BY startTime DESC")
    suspend fun getLoadingEventsAfter(userId: String, sevenDaysAgo: Date): List<LoadingEvent>

    @Query("SELECT * FROM loading_events WHERE isSynced = 0 AND endTime IS NOT NULL")
    suspend fun getUnsyncedLoadingEvents(): List<LoadingEvent>

    @Query("UPDATE loading_events SET isSynced = 1 WHERE id = :id")
    suspend fun setLoadingEventSynced(id: Long)

    @Transaction
    suspend fun replaceLoadingEvent(oldId: Long, newEvent: LoadingEvent) {
        deleteLoadingEventById(oldId)
        insertLoadingEvents(listOf(newEvent))
    }

    @Query("DELETE FROM loading_events WHERE isSynced = 1")
    suspend fun clearSyncedData()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoadingEvents(events: List<LoadingEvent>)
}
