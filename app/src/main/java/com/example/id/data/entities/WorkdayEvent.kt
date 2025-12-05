package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.Date
import com.example.id.data.entities.User

enum class EventType {
    WORK,
    VACATION,
    SICK_LEAVE
}

@Entity(tableName = "workday_events")
data class WorkdayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerializedName("userId") var userId: String, // A felhasználó azonosítója (pl. név)
    val role: String, // Sofőr vagy Második ember
    @SerializedName("startTime") val startTime: Date, // WORK esetén a kezdés időpontja
    @SerializedName("endTime") val endTime: Date?, // WORK esetén a befejezés időpontja
    @SerializedName("startDate") val startDate: Date?, // VACATION/SICK_LEAVE esetén a kezdő dátum
    @SerializedName("endDate") val endDate: Date?, // VACATION/SICK_LEAVE esetén a záró dátum
    @SerializedName("breakTime") val breakTime: Int = 0, // Break time in minutes
    @SerializedName("startLocation") val startLocation: String?,
    @SerializedName("startLatitude") val startLatitude: Double?,
    @SerializedName("startLongitude") val startLongitude: Double?,
    @SerializedName("endLocation") val endLocation: String?,
    @SerializedName("endLatitude") val endLatitude: Double?,
    @SerializedName("endLongitude") val endLongitude: Double?,
    @SerializedName("startOdometer") val startOdometer: Int?,
    @SerializedName("endOdometer") val endOdometer: Int?,
    @SerializedName("carPlate") val carPlate: String?, // Csak sofőr esetén
    val type: EventType = EventType.WORK,
    var isSynced: Boolean = false
) {
    @Ignore
    @SerializedName("User")
    val user: User? = null
}