package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class EventType {
    WORK,
    VACATION,
    SICK_LEAVE
}

@Entity(tableName = "workday_events")
data class WorkdayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String, // A felhasználó azonosítója (pl. név)
    val role: String, // Sofőr vagy Második ember
    val startTime: Date, // WORK esetén a kezdés időpontja
    val endTime: Date?, // WORK esetén a befejezés időpontja
    val startDate: Date?, // VACATION/SICK_LEAVE esetén a kezdő dátum
    val endDate: Date?, // VACATION/SICK_LEAVE esetén a záró dátum
    val breakTime: Int = 0, // Break time in minutes
    val startLocation: String?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLocation: String?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val startOdometer: Int?,
    val endOdometer: Int?,
    val carPlate: String?, // Csak sofőr esetén
    val type: EventType = EventType.WORK
)
