package com.example.id.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.id.data.model.WorkEvent
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface WorkEventDao {
    @Insert
    suspend fun insert(event: WorkEvent)

    @Query("SELECT * FROM work_events WHERE timestamp >= :dayStart ORDER BY timestamp ASC")
    suspend fun getEventsForDay(dayStart: Date): List<WorkEvent>

    @Query("SELECT * FROM work_events WHERE timestamp >= :dayStart ORDER BY timestamp ASC")
    fun getEventsForDayFlow(dayStart: Date): Flow<List<WorkEvent>>

    @Query("DELETE FROM work_events")
    suspend fun deleteAll()
}
