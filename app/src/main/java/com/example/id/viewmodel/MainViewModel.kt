package com.example.id.viewmodel

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.id.R
import com.example.id.data.AppRepository
import com.example.id.data.entities.BreakEvent
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.services.TrackingService
import com.example.id.util.DataManager
import com.example.id.util.NetworkClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// Data class for report items
data class WorkdayReportItem(
    val workday: WorkdayEvent,
    val totalBreakDuration: Long,
    val netWorkDuration: Long
)

const val ROLE_DRIVER = "driver"
const val CUMULATIVE_OVERTIME_KEY = "cumulative_overtime"

class MainViewModel(
    private val application: Application, 
    private val repository: AppRepository, 
    private val userId: String, 
    private val userRole: String,
    private val dataManager: DataManager
) : AndroidViewModel(application) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)
    private val prefs = application.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    // StateFlows for UI
    val isWorkdayStarted: StateFlow<Boolean> = repository.getActiveWorkdayEvent(userId)
        .map { it != null && it.endTime == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isBreakStarted: StateFlow<Boolean> = repository.getActiveBreakEvent(userId)
        .map { it != null && it.endTime == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isloadingStarted: StateFlow<Boolean> = repository.getActiveLoadingEvent(userId)
        .map { it != null && it.endTime == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _workDuration = MutableStateFlow(0L)
    val workDuration: StateFlow<Long> = _workDuration.asStateFlow()

    private val _breakDuration = MutableStateFlow(0L)
    val breakDuration: StateFlow<Long> = _breakDuration.asStateFlow()

    private val _overtime = MutableStateFlow(prefs.getLong(CUMULATIVE_OVERTIME_KEY, 0L))
    val overtime: StateFlow<Long> = _overtime.asStateFlow()

    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText.asStateFlow()

    private val _reportResults = MutableStateFlow<List<Any>>(emptyList())
    val reportResults: StateFlow<List<Any>> = _reportResults.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<Any>>(emptyList())
    val recentEvents: StateFlow<List<Any>> = _recentEvents.asStateFlow()

    private val _editingEvent = MutableStateFlow<Any?>(null)
    val editingEvent: StateFlow<Any?> = _editingEvent.asStateFlow()

    private val _measuredBreakDurationForEdit = MutableStateFlow(0L)
    val measuredBreakDurationForEdit: StateFlow<Long> = _measuredBreakDurationForEdit.asStateFlow()

    private var activeWorkdayEvent: WorkdayEvent? = null
    private var activeBreakEvent: BreakEvent? = null

    private val loadingControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.id.ACTION_START_LOADING" -> {
                    val startTime = intent.getLongExtra("START_TIME", System.currentTimeMillis())
                    startLoading(isAutomatic = true, startTime = Date(startTime))
                }
                "com.example.id.ACTION_END_LOADING" -> endLoading(isAutomatic = true)
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.getActiveWorkdayEvent(userId).collect { event ->
                activeWorkdayEvent = event
                if (event == null || event.endTime != null) {
                    updateDurations()
                } else {
                    // If workday is active, ensure service is running
                    Intent(application, TrackingService::class.java).also {
                        it.action = TrackingService.ACTION_START_OR_RESUME_SERVICE
                        it.putExtra("USER_ID", userId)
                        application.startService(it)
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.getActiveBreakEvent(userId).collect { event ->
                activeBreakEvent = event
            }
        }
        viewModelScope.launch {
            while (true) {
                if (isWorkdayStarted.value) {
                    updateDurations()
                }
                delay(1000)
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.id.ACTION_START_LOADING")
            addAction("com.example.id.ACTION_END_LOADING")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(loadingControlReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            application.registerReceiver(loadingControlReceiver, filter)
        }
    }

    override fun onCleared() {
        super.onCleared()
        application.unregisterReceiver(loadingControlReceiver)
    }

    private suspend fun updateDurations() {
        val currentWorkday = activeWorkdayEvent
        if (currentWorkday != null) {
            val workStart = currentWorkday.startTime.time
            val workEnd = currentWorkday.endTime?.time ?: System.currentTimeMillis()
            val breaks = repository.getBreaksForWorkday(currentWorkday.id).first()
            _breakDuration.value = calculateBreakDuration(breaks)
            _workDuration.value = workEnd - workStart - _breakDuration.value
        } else {
            _workDuration.value = 0L
            _breakDuration.value = 0L
        }
    }

    private fun calculateBreakDuration(breaks: List<BreakEvent>, effectiveTime: Long = System.currentTimeMillis()): Long {
        var totalBreakDuration = 0L
        breaks.forEach { breakEvent ->
            val start = breakEvent.startTime.time
            val end = breakEvent.endTime?.time ?: if (breakEvent.id == activeBreakEvent?.id) effectiveTime else start
            totalBreakDuration += (end - start)
        }
        return totalBreakDuration
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MainViewModel", "Location permissions not granted.")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainViewModel", "Error getting location: ${e.message}", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .addOnCanceledListener {
                    continuation.cancel()
                }
        }
    }

    @Suppress("DEPRECATION")
    private fun getAddressFromLocation(context: Context, location: Location?): String? {
        if (location == null) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0)
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e("MainViewModel", "Error getting address: ${e.message}", e)
            null
        }
    }

    fun startWorkday(odometer: Int?, carPlate: String?) {
        viewModelScope.launch {
            try {
                val location = getCurrentLocation()
                val address = getAddressFromLocation(application, location)
                val newWorkday = WorkdayEvent(
                    userId = userId,
                    role = userRole,
                    startTime = Date(),
                    endTime = null,
                    startDate = null,
                    endDate = null,
                    startLocation = address,
                    startLatitude = location?.latitude,
                    startLongitude = location?.longitude,
                    endLocation = null,
                    endLatitude = null,
                    endLongitude = null,
                    startOdometer = odometer,
                    endOdometer = null,
                    carPlate = carPlate
                )
                repository.insertWorkdayEvent(newWorkday)
                updateDurations()
                // Start Tracking Service
                Intent(application, TrackingService::class.java).also {
                    it.action = TrackingService.ACTION_START_OR_RESUME_SERVICE
                    it.putExtra("USER_ID", userId)
                    application.startService(it)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error starting workday: ${e.message}", e)
            }
        }
    }

    fun insertManualWorkday(
        startTime: Date,
        endTime: Date?,
        startLocation: String?,
        endLocation: String?,
        carPlate: String?,
        startOdometer: Int?,
        endOdometer: Int?,
        breakTime: Int,
        type: EventType
    ) {
        viewModelScope.launch {
            try {
                val newWorkday = WorkdayEvent(
                    userId = userId,
                    role = userRole,
                    startTime = startTime,
                    endTime = endTime,
                    startDate = if (type != EventType.WORK) startTime else null,
                    endDate = if (type != EventType.WORK) endTime else null,
                    breakTime = breakTime,
                    startLocation = startLocation,
                    startLatitude = null,
                    startLongitude = null,
                    endLocation = endLocation,
                    endLatitude = null,
                    endLongitude = null,
                    startOdometer = startOdometer,
                    endOdometer = endOdometer,
                    carPlate = carPlate,
                    type = type
                )
                repository.insertWorkdayEvent(newWorkday)
                loadRecentEvents()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error inserting manual workday", e)
            }
        }
    }

    fun endWorkday(odometer: Int?) {
        viewModelScope.launch {
            try {
                val currentWorkday = activeWorkdayEvent
                if (currentWorkday != null) {
                    val endTime = Date()
                    val location = getCurrentLocation()
                    val address = getAddressFromLocation(application, location)
                    val updatedWorkday = currentWorkday.copy(
                        endTime = endTime,
                        endLocation = address,
                        endLatitude = location?.latitude,
                        endLongitude = location?.longitude,
                        endOdometer = odometer
                    )
                    repository.updateWorkdayEvent(updatedWorkday)

                    // Calculate and save overtime
                    val breaks = repository.getBreaksForWorkday(currentWorkday.id).first()
                    val totalBreakDuration = calculateBreakDuration(breaks, endTime.time)
                    val netWorkDuration = endTime.time - currentWorkday.startTime.time - totalBreakDuration
                    val eightHoursInMillis = 8 * 60 * 60 * 1000L
                    if (netWorkDuration > eightHoursInMillis) {
                        val dailyOvertime = netWorkDuration - eightHoursInMillis
                        val currentCumulativeOvertime = prefs.getLong(CUMULATIVE_OVERTIME_KEY, 0L)
                        val newCumulativeOvertime = currentCumulativeOvertime + dailyOvertime
                        prefs.edit().putLong(CUMULATIVE_OVERTIME_KEY, newCumulativeOvertime).apply()
                        _overtime.value = newCumulativeOvertime
                    }

                    updateDurations()
                    // Stop Tracking Service
                    Intent(application, TrackingService::class.java).also {
                        it.action = TrackingService.ACTION_STOP_SERVICE
                        application.startService(it)
                    }
                    // Send to server
                    try {
                        val response = NetworkClient.instance.postWorkday(updatedWorkday)
                        if (response.isSuccessful) {
                            Log.d("MainViewModel", "Workday sent to server successfully.")
                        } else {
                            Log.e("MainViewModel", "Failed to send workday to server: ${response.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error sending workday to server: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error ending workday: ${e.message}", e)
            }
        }
    }

    fun startBreak() {
        viewModelScope.launch {
            try {
                val currentWorkday = activeWorkdayEvent
                if (currentWorkday != null) {
                    val newBreak = BreakEvent(
                        userId = userId,
                        workdayEventId = currentWorkday.id,
                        startTime = Date(),
                        endTime = null,
                        breakType = application.getString(R.string.pdf_header_break)
                    )
                    repository.insertBreakEvent(newBreak)
                    updateDurations()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error starting break: ${e.message}", e)
            }
        }
    }

    fun endBreak() {
        viewModelScope.launch {
            try {
                val currentBreak = activeBreakEvent
                if (currentBreak != null) {
                    val updatedBreak = currentBreak.copy(endTime = Date())
                    repository.updateBreakEvent(updatedBreak)
                    updateDurations()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error ending break: ${e.message}", e)
            }
        }
    }

    fun recordRefuel(odometer: Int, fuelType: String, fuelAmount: Double, carPlate: String, paymentMethod: String) {
        viewModelScope.launch {
            try {
                val location = getCurrentLocation()
                val address = getAddressFromLocation(application, location)
                val newRefuel = RefuelEvent(
                    userId = userId,
                    odometer = odometer,
                    fuelType = fuelType,
                    fuelAmount = fuelAmount,
                    carPlate = carPlate,
                    paymentMethod = paymentMethod,
                    timestamp = Date(),
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    location = address
                )
                repository.insertRefuelEvent(newRefuel)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error recording refuel: ${e.message}", e)
            }
        }
    }

    fun recordAbsence(type: String, startDate: Date, endDate: Date) {
        viewModelScope.launch {
            try {
                val eventType = when (type) {
                    "VACATION" -> EventType.VACATION
                    "SICK_LEAVE" -> EventType.SICK_LEAVE
                    else -> throw IllegalArgumentException("Unknown absence type: $type")
                }

                val newAbsence = WorkdayEvent(
                    userId = userId,
                    role = userRole,
                    startTime = startDate,
                    endTime = endDate,
                    startDate = startDate,
                    endDate = endDate,
                    breakTime = 0,
                    startLocation = null,
                    startLatitude = null,
                    startLongitude = null,
                    endLocation = null,
                    endLatitude = null,
                    endLongitude = null,
                    startOdometer = null,
                    endOdometer = null,
                    carPlate = null,
                    type = eventType
                )
                repository.insertWorkdayEvent(newAbsence)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error recording absence", e)
            }
        }
    }

    fun startLoading(isAutomatic: Boolean = false, startTime: Date = Date()) {
        viewModelScope.launch {
            if (isAutomatic && isBreakStarted.value) {
                Log.d("MainViewModel", "Automatic loading start ignored due to active break.")
                return@launch
            }
            if (isloadingStarted.value) {
                Log.d("MainViewModel", "Loading already started.")
                return@launch
            }
            try {
                val location = getCurrentLocation()
                val address = getAddressFromLocation(application, location)
                val currentWorkday = activeWorkdayEvent
                val newLoading = LoadingEvent(
                    userId = userId,
                    startTime = startTime,
                    endTime = null,
                    location = address,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    workdayEventId = currentWorkday?.id
                )
                repository.insertLoadingEvent(newLoading)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error starting loading: ${e.message}", e)
            }
        }
    }

    fun endLoading(isAutomatic: Boolean = false) {
        viewModelScope.launch {
            if (isAutomatic && !isloadingStarted.value) {
                Log.d("MainViewModel", "Automatic loading end ignored as no loading is active.")
                return@launch
            }
            try {
                val activeLoading = repository.getActiveLoadingEvent(userId).first()
                if (activeLoading != null) {
                    val updatedLoading = activeLoading.copy(endTime = Date())
                    repository.updateLoadingEvent(updatedLoading)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error ending loading: ${e.message}", e)
            }
        }
    }

    fun generateSummary(context: Context) {
        viewModelScope.launch {
            val today = Calendar.getInstance()
            val startOfDay = today.clone() as Calendar
            startOfDay.set(Calendar.HOUR_OF_DAY, 0)
            startOfDay.set(Calendar.MINUTE, 0)
            startOfDay.set(Calendar.SECOND, 0)
            startOfDay.set(Calendar.MILLISECOND, 0)

            val endOfDay = today.clone() as Calendar
            endOfDay.set(Calendar.HOUR_OF_DAY, 23)
            endOfDay.set(Calendar.MINUTE, 59)
            endOfDay.set(Calendar.SECOND, 59)
            endOfDay.set(Calendar.MILLISECOND, 999)

            val workdaysToday = repository.getWorkdayEventsForReport(userId, startOfDay.time, endOfDay.time).first()
            if (workdaysToday.isEmpty()) {
                _summaryText.value = context.getString(R.string.no_active_workday_for_summary)
                return@launch
            }

            val summary = StringBuilder()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
            val notAvailable = context.getString(R.string.not_available)
            val unitKm = context.getString(R.string.unit_km)

            summary.append("${context.getString(R.string.daily_summary)} - ${dateFormat.format(today.time)}\n")
            summary.append("============================\n")
            summary.append("${context.getString(R.string.user)}: $userId\n")

            var totalGrossWorkMillis = 0L
            var totalBreakMillis = 0L
            var totalLoadingMillis = 0L
            var firstOdometer: Int? = null
            var lastOdometer: Int? = null

            val workEventsToday = workdaysToday.filter { it.type == EventType.WORK }.sortedBy { it.startTime }

            // Find first and last odometer readings for the day
            firstOdometer = workEventsToday.firstNotNullOfOrNull { it.startOdometer }
            lastOdometer = workEventsToday.mapNotNull { it.endOdometer }.lastOrNull() ?: workEventsToday.mapNotNull { it.startOdometer }.lastOrNull()


            workEventsToday.forEach { workday ->
                summary.append("\n${context.getString(R.string.work_time).uppercase()} (${workday.carPlate ?: notAvailable})\n")
                summary.append("  ${context.getString(R.string.start_time)}: ${timeFormat.format(workday.startTime)} (${workday.startLocation ?: context.getString(R.string.unknown)})\n")
                workday.startOdometer?.let {
                    summary.append("  ${context.getString(R.string.start_odometer)}: $it $unitKm\n")
                }

                if (workday.endTime != null) {
                    summary.append("  ${context.getString(R.string.end_time)}: ${timeFormat.format(workday.endTime)} (${workday.endLocation ?: context.getString(R.string.unknown)})\n")
                    workday.endOdometer?.let {
                        summary.append("  ${context.getString(R.string.end_odometer)}: $it $unitKm\n")
                    }
                    totalGrossWorkMillis += workday.endTime.time - workday.startTime.time
                } else {
                    summary.append("  ${context.getString(R.string.end_time)}: ${context.getString(R.string.running)}...\n")
                    totalGrossWorkMillis += System.currentTimeMillis() - workday.startTime.time
                }

                val breaks = repository.getBreaksForWorkday(workday.id).first()
                if (breaks.isNotEmpty()) {
                    summary.append("  ${context.getString(R.string.breaks).uppercase()}:\n")
                    breaks.forEach { breakEvent ->
                        val breakEndTime = breakEvent.endTime ?: Date()
                        val duration = breakEndTime.time - breakEvent.startTime.time
                        totalBreakMillis += duration
                        summary.append("    - ${timeFormat.format(breakEvent.startTime)} - ${timeFormat.format(breakEndTime)} (${formatDuration(duration)})\n")
                    }
                }

                val loadings = repository.getLoadingsForWorkday(workday.id).first()
                if (loadings.isNotEmpty()) {
                    summary.append("  ${context.getString(R.string.loadings).uppercase()}:\n")
                    loadings.forEach { loadingEvent ->
                        loadingEvent.startTime?.let { startTime ->
                            val loadingEndTime = loadingEvent.endTime ?: Date()
                            val duration = loadingEndTime.time - startTime.time
                            totalLoadingMillis += duration
                            summary.append("    - ${timeFormat.format(startTime)} - ${timeFormat.format(loadingEndTime)} (${formatDuration(duration)})\n")
                            summary.append("      ${context.getString(R.string.location)}: ${loadingEvent.location ?: notAvailable}\n")
                        }
                    }
                }
            }

            // --- Tankolások ---
            val refuelsToday = repository.getRefuelEventsForReport(userId, startOfDay.time, endOfDay.time, null, null, null).first()
            if (refuelsToday.isNotEmpty()) {
                summary.append("\n${context.getString(R.string.refuelings).uppercase()}\n")
                refuelsToday.forEach { refuel ->
                    summary.append("  - ${timeFormat.format(refuel.timestamp)}: ${refuel.fuelAmount}${context.getString(R.string.liter_unit)} ${refuel.fuelType}\n")
                    summary.append("    ${context.getString(R.string.odometer)}: ${refuel.odometer} $unitKm, ${context.getString(R.string.car_plate)}: ${refuel.carPlate}\n")
                }
            }

            // --- Összesítés ---
            val netWorkMillis = totalGrossWorkMillis - totalBreakMillis
            summary.append("\n${context.getString(R.string.summary).uppercase()}\n")
            summary.append("  ${context.getString(R.string.gross_work_time)}: ${formatDuration(totalGrossWorkMillis)}\n")
            summary.append("  ${context.getString(R.string.total_break_time)}: ${formatDuration(totalBreakMillis)}\n")
            if (totalLoadingMillis > 0) {
                summary.append("  ${context.getString(R.string.loading)}: ${formatDuration(totalLoadingMillis)}\n")
            }
            summary.append("  ${context.getString(R.string.net_work_time)}: ${formatDuration(netWorkMillis)}\n")
            if (firstOdometer != null && lastOdometer != null) {
                summary.append("  ${context.getString(R.string.driven_distance)}: ${lastOdometer!! - firstOdometer!!} $unitKm\n")
            }

            _summaryText.value = summary.toString()
        }
    }

    private fun parseDate(dateString: String): Date? {
        if (dateString.isBlank()) return null
        return try {
            SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).parse(dateString)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Date parsing failed for: $dateString", e)
            null
        }
    }

    fun runReport(
        eventTypeKey: String,
        startDateString: String,
        endDateString: String,
        carPlate: String?,
        fuelType: String?,
        paymentMethod: String?
    ) {
        viewModelScope.launch {
            val startDate = parseDate(startDateString)
            val endDate = parseDate(endDateString)?.let { Date(it.time + TimeUnit.DAYS.toMillis(1) - 1) } // End of day

            _reportResults.value = emptyList() // Clear previous results

            try {
                when (eventTypeKey) {
                    "work_time" -> {
                        val workdays = repository.getWorkdayEventsForReport(userId, startDate, endDate).first()
                        val filteredWorkdays = workdays.filter { event ->
                            if (carPlate.isNullOrBlank()) {
                                true // Keep all events if no plate is specified
                            } else {
                                // Keep non-work events, and only filter work events by plate
                                event.type != EventType.WORK || event.carPlate?.contains(carPlate, ignoreCase = true) == true
                            }
                        }
                        val reportItems = filteredWorkdays.map { workday ->
                            val breaks = repository.getBreaksForWorkday(workday.id).first()
                            val totalBreakDuration = if (breaks.isNotEmpty()) {
                                calculateBreakDuration(breaks, workday.endTime?.time ?: System.currentTimeMillis())
                            } else {
                                TimeUnit.MINUTES.toMillis(workday.breakTime.toLong())
                            }
                            val totalWorkDuration = if (workday.endTime != null) {
                                workday.endTime.time - workday.startTime.time
                            } else {
                                0L
                            }
                            val netWorkDuration = if(totalWorkDuration > 0) totalWorkDuration - totalBreakDuration else 0L
                            WorkdayReportItem(workday, totalBreakDuration, netWorkDuration)
                        }
                        _reportResults.value = reportItems
                    }
                    "refueling" -> {
                        _reportResults.value = repository.getRefuelEventsForReport(
                            userId = userId,
                            startDate = startDate,
                            endDate = endDate,
                            carPlate = carPlate?.takeIf { it.isNotBlank() },
                            fuelType = fuelType?.takeIf { it.isNotBlank() },
                            paymentMethod = paymentMethod?.takeIf { it.isNotBlank() }
                        ).first()
                    }
                    "loading" -> {
                        val loadings = repository.getLoadingEventsForReport(userId, startDate, endDate).first()
                        val filteredLoadings = if (carPlate.isNullOrBlank()) {
                            loadings
                        } else {
                            val workdayIdsWithPlate = repository.getWorkdayEventsByPlate(userId, carPlate).first().map { it.id }
                            loadings.filter { it.workdayEventId in workdayIdsWithPlate }
                        }
                        _reportResults.value = filteredLoadings
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error running report", e)
            }
        }
    }

    fun loadRecentEvents() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val startDate = cal.time

            val workdays = repository.getWorkdayEventsForReport(userId, startDate, null).first()
            val refuels = repository.getRefuelEventsForReport(userId, startDate, null, null, null, null).first()
            val loadings = repository.getLoadingEventsForReport(userId, startDate, null).first()

            val allEvents = (workdays + refuels + loadings).sortedByDescending {
                when (it) {
                    is WorkdayEvent -> it.startTime
                    is RefuelEvent -> it.timestamp
                    is LoadingEvent -> it.startTime
                    else -> Date(0)
                }
            }
            _recentEvents.value = allEvents
        }
    }

    fun deleteWorkdayEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkdayEventById(id)
            loadRecentEvents() // Refresh list
        }
    }

    fun deleteRefuelEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteRefuelEventById(id)
            loadRecentEvents() // Refresh list
        }
    }

    fun deleteLoadingEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteLoadingEventById(id)
            loadRecentEvents() // Refresh list
        }
    }

    // --- Edit Functions ---

    fun loadWorkdayForEdit(id: Long) {
        viewModelScope.launch {
            val event = repository.getWorkdayEventById(id)
            _editingEvent.value = event
            if (event != null) {
                val breaks = repository.getBreaksForWorkday(event.id).first()
                _measuredBreakDurationForEdit.value = calculateBreakDuration(breaks, event.endTime?.time ?: System.currentTimeMillis())
            }
        }
    }

    fun loadRefuelForEdit(id: Long) {
        viewModelScope.launch {
            _editingEvent.value = repository.getRefuelEventById(id)
        }
    }

    fun loadLoadingForEdit(id: Long) {
        viewModelScope.launch {
            _editingEvent.value = repository.getLoadingEventById(id)
        }
    }

    fun updateWorkday(event: WorkdayEvent) {
        viewModelScope.launch {
            val oldEvent = repository.getWorkdayEventById(event.id)
            if (oldEvent != null) {
                // Overtime calculation for WORK type
                if (oldEvent.type == EventType.WORK && oldEvent.endTime != null) {
                    val eightHoursInMillis = 8 * 60 * 60 * 1000L

                    // Calculate old overtime
                    val oldBreaks = repository.getBreaksForWorkday(oldEvent.id).first()
                    val oldTotalBreakDuration = if (oldBreaks.isNotEmpty()) calculateBreakDuration(oldBreaks, oldEvent.endTime!!.time) else TimeUnit.MINUTES.toMillis(oldEvent.breakTime.toLong())
                    val oldNetWorkDuration = oldEvent.endTime!!.time - oldEvent.startTime.time - oldTotalBreakDuration
                    val oldDailyOvertime = if (oldNetWorkDuration > eightHoursInMillis) oldNetWorkDuration - eightHoursInMillis else 0L

                    // Calculate new overtime
                    var newDailyOvertime = 0L
                    if (event.endTime != null) {
                        val newBreaks = repository.getBreaksForWorkday(event.id).first()
                        val newTotalBreakDuration = if (newBreaks.isNotEmpty()) calculateBreakDuration(newBreaks, event.endTime!!.time) else TimeUnit.MINUTES.toMillis(event.breakTime.toLong())
                        val newNetWorkDuration = event.endTime!!.time - event.startTime.time - newTotalBreakDuration
                        if (newNetWorkDuration > eightHoursInMillis) {
                            newDailyOvertime = newNetWorkDuration - eightHoursInMillis
                        }
                    }

                    val overtimeDifference = newDailyOvertime - oldDailyOvertime
                    if (overtimeDifference != 0L) {
                        val currentCumulativeOvertime = prefs.getLong(CUMULATIVE_OVERTIME_KEY, 0L)
                        val newCumulativeOvertime = currentCumulativeOvertime + overtimeDifference
                        prefs.edit().putLong(CUMULATIVE_OVERTIME_KEY, newCumulativeOvertime).apply()
                        _overtime.value = newCumulativeOvertime
                    }
                }
            }
            
            repository.updateWorkdayEvent(event)
            clearEditingEvent()
            loadRecentEvents()
        }
    }

    fun updateRefuel(event: RefuelEvent) {
        viewModelScope.launch {
            repository.updateRefuelEvent(event)
            clearEditingEvent()
            loadRecentEvents()
        }
    }

    fun updateLoading(event: LoadingEvent) {
        viewModelScope.launch {
            repository.updateLoadingEvent(event)
            clearEditingEvent()
            loadRecentEvents()
        }
    }

    fun clearEditingEvent() {
        _editingEvent.value = null
        _measuredBreakDurationForEdit.value = 0L
    }

    fun deleteAllEvents() {
        viewModelScope.launch {
            Log.w("MainViewModel", "deleteAllEvents not fully implemented yet for Room.")
            _workDuration.value = 0L
            _breakDuration.value = 0L
        }
    }

    fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun resetOvertime() {
        viewModelScope.launch {
            prefs.edit().putLong(CUMULATIVE_OVERTIME_KEY, 0L).apply()
            _overtime.value = 0L
        }
    }

    // --- Data Management ---
    fun backupDatabase() {
        dataManager.backupDatabase()
    }

    fun restoreDatabase(uri: Uri): Boolean {
        return dataManager.restoreDatabase(uri)
    }
}
