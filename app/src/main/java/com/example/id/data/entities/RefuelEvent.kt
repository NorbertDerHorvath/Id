package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "refuel_events")
data class RefuelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val odometer: Int,
    val fuelType: String,
    val fuelAmount: Double,
    val carPlate: String,
    val paymentMethod: String,
    val timestamp: Date,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    val isSynced: Boolean = false
)
