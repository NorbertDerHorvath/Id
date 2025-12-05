package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "loading_events")
data class LoadingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val startTime: Date?,
    val endTime: Date?,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val workdayEventId: Long?, // Hivatkozás a munkaidő eseményre
    val isSynced: Boolean = false
)
