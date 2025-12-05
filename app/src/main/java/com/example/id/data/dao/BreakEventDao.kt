package com.example.id.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.id.data.entities.BreakEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakEventDao {
    @Insert
    suspend fun insertBreakEvent(event: BreakEvent): Long

    @Update
    suspend fun updateBreakEvent(event: BreakEvent)

    @Query("SELECT * FROM break_events WHERE userId = :userId ORDER BY startTime DESC")
    fun getAllBreakEvents(userId: String): Flow<List<BreakEvent>>

    @Query("SELECT * FROM break_events WHERE workdayEventId = :workdayEventId ORDER BY startTime DESC")
    fun getBreaksForWorkday(workdayEventId: Long): Flow<List<BreakEvent>>

    @Query("SELECT * FROM break_events WHERE userId = :userId AND endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun getActiveBreakEvent(userId: String): Flow<BreakEvent?>

    @Query("SELECT * FROM break_events WHERE isSynced = 0")
    suspend fun getUnsyncedBreakEvents(): List<BreakEvent>

    @Query("UPDATE break_events SET isSynced = 1 WHERE id = :id")
    suspend fun setBreakEventSynced(id: Long)
}
