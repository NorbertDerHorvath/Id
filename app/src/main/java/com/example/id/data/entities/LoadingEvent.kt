package com.example.id.data.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.Date
import com.example.id.data.entities.User

@Entity(tableName = "loading_events")
data class LoadingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerializedName("userId") var userId: String,
    val startTime: Date?,
    val endTime: Date?,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val workdayEventId: Long?, // Hivatkozás a munkaidő eseményre
    var isSynced: Boolean = false
) {
    @Ignore
    @SerializedName("User")
    val user: User? = null
}