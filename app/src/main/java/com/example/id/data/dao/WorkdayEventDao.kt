package com.example.id.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.id.data.entities.WorkdayEvent
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface WorkdayEventDao {
    @Insert
    suspend fun insertWorkdayEvent(event: WorkdayEvent): Long

    @Update
    suspend fun updateWorkdayEvent(event: WorkdayEvent)

    @Query("SELECT * FROM workday_events WHERE userId = :userId ORDER BY startTime DESC")
    fun getAllWorkdayEvents(userId: String): Flow<List<WorkdayEvent>>

    @Query("SELECT * FROM workday_events WHERE id = :id")
    suspend fun getWorkdayEventById(id: Long): WorkdayEvent?

    @Query("SELECT * FROM workday_events WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getActiveWorkdayEvent(): Flow<WorkdayEvent?>

    @Query("SELECT * FROM workday_events WHERE userId = :userId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getActiveWorkdayEvent(userId: String): Flow<WorkdayEvent?>

    @Query("""
        SELECT * FROM workday_events
        WHERE userId = :userId
        AND (:startDate IS NULL OR startTime >= :startDate)
        AND (:endDate IS NULL OR startTime < :endDate)
        ORDER BY startTime DESC
        """)
    fun getWorkdayEventsForReport(userId: String, startDate: Date?, endDate: Date?): Flow<List<WorkdayEvent>>

    @Query("DELETE FROM workday_events WHERE id = :id")
    suspend fun deleteWorkdayEventById(id: Long)

    @Query("SELECT * FROM workday_events WHERE userId = :userId AND carPlate LIKE '%' || :carPlate || '%'" )
    fun getWorkdayEventsByPlate(userId: String, carPlate: String): Flow<List<WorkdayEvent>>

    @Query("SELECT * FROM workday_events WHERE userId = :userId AND startTime >= :sevenDaysAgo ORDER BY startTime DESC")
    suspend fun getWorkdayEventsAfter(userId: String, sevenDaysAgo: Date): List<WorkdayEvent>

    @Query("SELECT * FROM workday_events WHERE isSynced = 0")
    suspend fun getUnsyncedWorkdayEvents(): List<WorkdayEvent>

    @Query("UPDATE workday_events SET isSynced = 1 WHERE id = :id")
    suspend fun setWorkdayEventSynced(id: Long)

    @Transaction
    suspend fun replaceWorkdayEvent(oldId: Long, newEvent: WorkdayEvent) {
        deleteWorkdayEventById(oldId)
        insertWorkdayEvent(newEvent.copy(id = 0))
    }
}
