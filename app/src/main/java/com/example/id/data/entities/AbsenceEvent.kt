package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "absence_events")
data class AbsenceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val type: String, // e.g., "VACATION", "SICK_LEAVE"
    val startDate: Date,
    val endDate: Date
)
