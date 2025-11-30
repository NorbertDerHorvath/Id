package com.example.id.data.dao

import androidx.room.Dao
import androidx.room.Insert
import com.example.id.data.entities.AbsenceEvent

@Dao
interface AbsenceEventDao {
    @Insert
    suspend fun insertAbsenceEvent(event: AbsenceEvent)
}
