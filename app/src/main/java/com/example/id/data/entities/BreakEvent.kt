package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "break_events")
data class BreakEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val workdayEventId: Long, // Hivatkozás a munkaidő eseményre
    val startTime: Date,
    val endTime: Date?,
    val breakType: String?, // Pl. "Szünet", "Lerakó", "Egyéb"
    val isSynced: Boolean = false
)
