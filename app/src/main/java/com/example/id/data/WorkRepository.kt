package com.example.id.data

import android.content.Context
import com.example.id.data.local.AppDatabase
import com.example.id.data.model.EventType
import com.example.id.data.model.WorkEvent
import com.example.id.util.GeocoderHelper
import com.example.id.util.LocationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date

class WorkRepository(context: Context) {

    private val workEventDao = AppDatabase.getDatabase(context).workEventDao()
    private val locationProvider = LocationProvider(context)
    private val geocoderHelper = GeocoderHelper(context)

    private val dailyEventsFlow = workEventDao.getEventsForDayFlow(getTodayStart())

    val isWorkdayStarted: Flow<Boolean> = dailyEventsFlow.map { events ->
        events.lastOrNull { it.type == EventType.WORK_START || it.type == EventType.WORK_END }?.type == EventType.WORK_START
    }

    val isBreakStarted: Flow<Boolean> = dailyEventsFlow.map { events ->
        events.lastOrNull { it.type == EventType.BREAK_START || it.type == EventType.BREAK_END }?.type == EventType.BREAK_START
    }

    val workDuration: Flow<Long> = dailyEventsFlow.map { calculateDurations(it).first }
    val breakDuration: Flow<Long> = dailyEventsFlow.map { calculateDurations(it).second }

    suspend fun startWorkday(odometer: Int) {
        saveEvent(EventType.WORK_START, odometer = odometer)
    }

    suspend fun endWorkday(odometer: Int) {
        saveEvent(EventType.WORK_END, odometer = odometer)
    }

    suspend fun startBreak() {
        saveEvent(EventType.BREAK_START)
    }

    suspend fun endBreak() {
        saveEvent(EventType.BREAK_END)
    }

    suspend fun recordRefuel(odometer: Int, fuelType: String, fuelAmount: Double, paymentMethod: String) {
        saveEvent(EventType.REFUEL, odometer = odometer, fuelType = fuelType, fuelAmount = fuelAmount, paymentMethod = paymentMethod)
    }

    suspend fun getEventsForToday(): List<WorkEvent> {
        return workEventDao.getEventsForDay(getTodayStart())
    }

    suspend fun deleteAllEvents() {
        workEventDao.deleteAll()
    }

    private suspend fun saveEvent(
        type: EventType,
        odometer: Int? = null,
        fuelType: String? = null,
        fuelAmount: Double? = null,
        paymentMethod: String? = null
    ) {
        val location = locationProvider.currentLocation.first()
        val address = geocoderHelper.getAddressFromCoordinates(location.latitude, location.longitude)

        val event = WorkEvent(
            type = type,
            timestamp = Date(),
            latitude = location.latitude,
            longitude = location.longitude,
            address = address,
            odometer = odometer,
            fuelType = fuelType,
            fuelAmount = fuelAmount,
            paymentMethod = paymentMethod
        )
        workEventDao.insert(event)
    }

    private fun calculateDurations(events: List<WorkEvent>): Pair<Long, Long> {
        var workMillis = 0L
        var breakMillis = 0L
        var lastWorkStart: Date? = null
        var lastBreakStart: Date? = null

        for (event in events) {
            when (event.type) {
                EventType.WORK_START -> {
                    if (lastBreakStart == null) { // Do not start work if on break
                        lastWorkStart = event.timestamp
                    }
                }
                EventType.WORK_END -> {
                    lastWorkStart?.let { workMillis += event.timestamp.time - it.time }
                    lastWorkStart = null
                }
                EventType.BREAK_START -> {
                    lastWorkStart?.let { workMillis += event.timestamp.time - it.time } // Stop work timer
                    lastWorkStart = null
                    lastBreakStart = event.timestamp
                }
                EventType.BREAK_END -> {
                    lastBreakStart?.let { breakMillis += event.timestamp.time - it.time }
                    lastBreakStart = null
                    lastWorkStart = event.timestamp // Resume work timer
                }
                else -> {}
            }
        }

        val now = Date().time
        // Add ongoing duration ONLY if not on break
        if (lastBreakStart == null) {
            lastWorkStart?.let { workMillis += now - it.time }
        }
        lastBreakStart?.let { breakMillis += now - it.time }

        return Pair(workMillis, breakMillis)
    }

    private fun getTodayStart(): Date {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}