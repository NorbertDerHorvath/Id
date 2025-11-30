package com.example.id.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "work_events")
data class WorkEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val timestamp: Date,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val odometer: Int? = null,
    val fuelType: String? = null,
    val fuelAmount: Double? = null,
    val paymentMethod: String? = null // Added new field
)

enum class EventType {
    WORK_START,
    WORK_END,
    BREAK_START,
    BREAK_END,
    REFUEL
}
