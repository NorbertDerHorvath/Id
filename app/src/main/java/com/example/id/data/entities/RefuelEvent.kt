package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.Date
import com.example.id.data.entities.User

@Entity(tableName = "refuel_events")
data class RefuelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerializedName("userId") var userId: String,
    val odometer: Int,
    val fuelType: String,
    val fuelAmount: Double,
    val carPlate: String,
    val paymentMethod: String,
    val timestamp: Date,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val location: String? = null,
    var isSynced: Boolean = false
) {
    @Ignore
    @SerializedName("User")
    val user: User? = null
}