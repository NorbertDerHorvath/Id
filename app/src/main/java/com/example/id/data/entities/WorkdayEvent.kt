package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.Date

enum class EventType {
    WORK,
    VACATION,
    SICK_LEAVE
}

@Entity(tableName = "workday_events")
data class WorkdayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerializedName("user_id") val userId: String, // A felhasználó azonosítója (pl. név)
    val role: String, // Sofőr vagy Második ember
    @SerializedName("start_time") val startTime: Date, // WORK esetén a kezdés időpontja
    @SerializedName("end_time") val endTime: Date?, // WORK esetén a befejezés időpontja
    @SerializedName("start_date") val startDate: Date?, // VACATION/SICK_LEAVE esetén a kezdő dátum
    @SerializedName("end_date") val endDate: Date?, // VACATION/SICK_LEAVE esetén a záró dátum
    @SerializedName("break_time") val breakTime: Int = 0, // Break time in minutes
    @SerializedName("start_location") val startLocation: String?,
    @SerializedName("start_latitude") val startLatitude: Double?,
    @SerializedName("start_longitude") val startLongitude: Double?,
    @SerializedName("end_location") val endLocation: String?,
    @SerializedName("end_latitude") val endLatitude: Double?,
    @SerializedName("end_longitude") val endLongitude: Double?,
    @SerializedName("start_odometer") val startOdometer: Int?,
    @SerializedName("end_odometer") val endOdometer: Int?,
    @SerializedName("car_plate") val carPlate: String?, // Csak sofőr esetén
    val type: EventType = EventType.WORK,
    val isSynced: Boolean = false
)
