package com.example.id.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.id.data.entities.RefuelEvent
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface RefuelEventDao {
    @Insert
    suspend fun insertRefuelEvent(event: RefuelEvent): Long

    @Update
    suspend fun updateRefuelEvent(event: RefuelEvent)

    @Query("SELECT * FROM refuel_events WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllRefuelEvents(userId: String): Flow<List<RefuelEvent>>

    @Query("SELECT * FROM refuel_events WHERE carPlate = :carPlate ORDER BY timestamp DESC")
    fun getRefuelEventsByCar(carPlate: String): Flow<List<RefuelEvent>>

    @Query("""
        SELECT * FROM refuel_events
        WHERE userId = :userId
        AND (:startDate IS NULL OR timestamp >= :startDate)
        AND (:endDate IS NULL OR timestamp < :endDate)
        AND (:carPlate IS NULL OR carPlate LIKE '%' || :carPlate || '%')
        AND (:fuelType IS NULL OR fuelType = :fuelType)
        AND (:paymentMethod IS NULL OR paymentMethod = :paymentMethod)
        ORDER BY timestamp DESC
        """)
    fun getRefuelEventsForReport(userId: String, startDate: Date?, endDate: Date?, carPlate: String?, fuelType: String?, paymentMethod: String?): Flow<List<RefuelEvent>>

    @Query("SELECT * FROM refuel_events WHERE id = :id")
    suspend fun getRefuelEventById(id: Long): RefuelEvent?

    @Query("DELETE FROM refuel_events WHERE id = :id")
    suspend fun deleteRefuelEventById(id: Long)

    @Query("SELECT * FROM refuel_events WHERE userId = :userId AND timestamp >= :sevenDaysAgo ORDER BY timestamp DESC")
    suspend fun getRefuelEventsAfter(userId: String, sevenDaysAgo: Date): List<RefuelEvent>

    @Query("SELECT * FROM refuel_events WHERE isSynced = 0")
    suspend fun getUnsyncedRefuelEvents(): List<RefuelEvent>

    @Query("UPDATE refuel_events SET isSynced = 1 WHERE id = :id")
    suspend fun setRefuelEventSynced(id: Long)

    @Transaction
    suspend fun replaceRefuelEvent(oldId: Long, newEvent: RefuelEvent) {
        deleteRefuelEventById(oldId)
        insertRefuelEvent(newEvent.copy(id = 0))
    }
}
