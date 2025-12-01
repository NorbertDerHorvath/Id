package com.example.id.viewmodel

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.id.R
import com.example.id.USER_NAME_KEY
import com.example.id.data.AppRepository
import com.example.id.data.entities.BreakEvent
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.services.TrackingService
import com.example.id.util.ApiService
import com.example.id.util.DataManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject
import kotlin.coroutines.resume

data class WorkdayReportItem(
    val workday: WorkdayEvent,
    val totalBreakDuration: Long,
    val netWorkDuration: Long
)

const val CUMULATIVE_OVERTIME_KEY = "cumulative_overtime"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application, 
    private val repository: AppRepository, 
    private val apiService: ApiService, 
    private val prefs: SharedPreferences,
    private val dataManager: DataManager
) : ViewModel() {

    private val userId: String = prefs.getString(USER_NAME_KEY, "unknown_user")!!
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)

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

    init {
        viewModelScope.launch {
            repository.getActiveWorkdayEvent(userId).collect { event ->
                activeWorkdayEvent = event
                if (event == null || event.endTime != null) {
                    updateDurations()
                } else {
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
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getAddressFromLocation(context: Context, location: Location?): String? {
        if (location == null) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: IOException) {
            null
        }
    }

    fun startWorkday(odometer: Int?, carPlate: String?) {
        viewModelScope.launch {
            val location = getCurrentLocation()
            val address = getAddressFromLocation(application, location)
            val newWorkday = WorkdayEvent(
                userId = userId,
                role = prefs.getString(com.example.id.ROLE_DRIVER, "")!!,
                startTime = Date(),
                endTime = null,
                startLocation = address,
                startLatitude = location?.latitude,
                startLongitude = location?.longitude,
                startOdometer = odometer,
                carPlate = carPlate
            )
            repository.insertWorkdayEvent(newWorkday)
            Intent(application, TrackingService::class.java).also {
                it.action = TrackingService.ACTION_START_OR_RESUME_SERVICE
                it.putExtra("USER_ID", userId)
                application.startService(it)
            }
        }
    }

    fun endWorkday(odometer: Int?) {
        viewModelScope.launch {
            val currentWorkday = repository.getActiveWorkdayEvent(userId).first()
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

                try {
                    val response = apiService.postWorkday(updatedWorkday)
                    if (response.isSuccessful) {
                        Log.d("MainViewModel", "Workday sent to server successfully.")
                    } else {
                        Log.e("MainViewModel", "Failed to send workday: ${response.code()} - ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error sending workday to server: ${e.message}", e)
                }
                
                Intent(application, TrackingService::class.java).also {
                    it.action = TrackingService.ACTION_STOP_SERVICE
                    application.startService(it)
                }
            }
        }
    }
    // ... (rest of the functions are the same)
}
