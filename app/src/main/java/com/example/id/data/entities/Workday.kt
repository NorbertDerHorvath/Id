package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "workdays")
data class Workday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val startTime: Date,
    var endTime: Date? = null,
    var startLocation: String? = null,
    var endLocation: String? = null,
    var startOdometer: Int? = null,
    var endOdometer: Int? = null,
    var carPlate: String? = null,
    var startDate: Date? = null,
    var endDate: Date? = null
)
