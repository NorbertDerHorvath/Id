package com.example.id.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.id.USER_NAME_KEY
import com.example.id.data.AppRepository
import com.example.id.data.entities.BreakEvent
import com.example.id.data.entities.EventType
import com.example.id.data.entities.LoadingEvent
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.repository.AuthRepository
import com.example.id.worker.SyncWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val repository: AppRepository,
    private val authRepository: AuthRepository,
    private val prefs: SharedPreferences,
    private val workManager: WorkManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    private val userId: String
        get() = prefs.getString(USER_NAME_KEY, "unknown_user")!!

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)

    private val _isWorkdayStarted = MutableStateFlow(false)
    val isWorkdayStarted: StateFlow<Boolean> = _isWorkdayStarted.asStateFlow()

    private val _isBreakStarted = MutableStateFlow(false)
    val isBreakStarted: StateFlow<Boolean> = _isBreakStarted.asStateFlow()

    private val _isloadingStarted = MutableStateFlow(false)
    val isloadingStarted: StateFlow<Boolean> = _isloadingStarted.asStateFlow()

    private val _workDuration = MutableStateFlow(0L)
    val workDuration: StateFlow<Long> = _workDuration.asStateFlow()
    private val _breakDuration = MutableStateFlow(0L)
    val breakDuration: StateFlow<Long> = _breakDuration.asStateFlow()
    private val _overtime = MutableStateFlow(prefs.getLong("cumulative_overtime", 0L))
    val overtime: StateFlow<Long> = _overtime.asStateFlow()
    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText.asStateFlow()
    private val _workdayQueryResults = MutableStateFlow<List<WorkdayEvent>>(emptyList())
    val workdayQueryResults: StateFlow<List<WorkdayEvent>> = _workdayQueryResults.asStateFlow()
    private val _refuelQueryResults = MutableStateFlow<List<RefuelEvent>>(emptyList())
    val refuelQueryResults: StateFlow<List<RefuelEvent>> = _refuelQueryResults.asStateFlow()
    private val _recentEvents = MutableStateFlow<List<Any>>(emptyList())
    val recentEvents: StateFlow<List<Any>> = _recentEvents.asStateFlow()
    private val _editingEvent = MutableStateFlow<Any?>(null)
    val editingEvent: StateFlow<Any?> = _editingEvent.asStateFlow()
    private val _measuredBreakDurationForEdit = MutableStateFlow(0L)
    val measuredBreakDurationForEdit: StateFlow<Long> = _measuredBreakDurationForEdit.asStateFlow()
    private var activeWorkdayEvent: WorkdayEvent? = null
    private var activeBreakEvent: BreakEvent? = null

    private val _recentWorkdays = MutableStateFlow<List<WorkdayEvent>>(emptyList())
    val recentWorkdays: StateFlow<List<WorkdayEvent>> = _recentWorkdays.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            val token = authRepository.getToken()
            if (token == null) {
                _loginState.value = LoginUiState.Idle
                return@launch
            }

            try {
                val response = authRepository.validateToken()
                if (response.isSuccessful && response.body()?.valid == true) {
                    _loginState.value = LoginUiState.Success
                    observeUserData()
                } else {
                    authRepository.clearToken()
                    _loginState.value = LoginUiState.Error("Invalid token")
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.message ?: "Network error")
            }
        }
    }

    private fun observeUserData() {
        viewModelScope.launch {
            val currentUserId = userId
            if (currentUserId == "unknown_user") {
                _isWorkdayStarted.value = false
                _isBreakStarted.value = false
                _isloadingStarted.value = false
                activeWorkdayEvent = null
                activeBreakEvent = null
                return@launch
            }

            launch {
                repository.getActiveWorkdayEvent(currentUserId).collect { workday ->
                    activeWorkdayEvent = workday
                    _isWorkdayStarted.value = workday != null && workday.endTime == null

                    if (workday == null || workday.endTime != null) {
                        updateDurations()
                    }
                }
            }
            launch {
                repository.getActiveBreakEvent(currentUserId).collect { breakEvent ->
                    activeBreakEvent = breakEvent
                    _isBreakStarted.value = breakEvent != null && breakEvent.endTime == null
                }
            }
            launch {
                repository.getActiveLoadingEvent(currentUserId).collect { loadingEvent ->
                    _isloadingStarted.value = loadingEvent != null
                }
            }
            launch {
                repository.getAllWorkdayEvents(currentUserId).collect { workdays ->
                    _recentWorkdays.value = workdays.sortedByDescending { it.startTime }
                }
            }

            launch {
                while (true) {
                    if (_isWorkdayStarted.value) {
                        updateDurations()
                    }
                    delay(1000)
                }
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            try {
                val response = authRepository.login(username, password)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    authRepository.saveToken(body.token)
                    prefs.edit().putString(USER_NAME_KEY, username).apply()
                    initialize()
                    triggerSync()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown login error"
                    _loginState.value = LoginUiState.Error(errorBody)
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.clearToken()
            prefs.edit().remove(USER_NAME_KEY).apply()
            _loginState.value = LoginUiState.Idle
            activeWorkdayEvent = null
            activeBreakEvent = null
            _isWorkdayStarted.value = false
            _isBreakStarted.value = false
            _isloadingStarted.value = false
            _workDuration.value = 0L
            _breakDuration.value = 0L
        }
    }


    private suspend fun updateDurations() {
        val currentWorkday = activeWorkdayEvent
        if (currentWorkday != null) {
            val workStart = currentWorkday.startTime.time
            val workEnd = currentWorkday.endTime?.time ?: System.currentTimeMillis()
            val breaks = repository.getBreaksForWorkday(currentWorkday.localId).first()
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

    fun startWorkday(odometer: Int?, carPlate: String?) {
        viewModelScope.launch {
            val location = getCurrentLocation()
            val address = getAddressFromLocation(application, location)
            val newWorkday = WorkdayEvent(
                userId = userId,
                role = prefs.getString("user_role", "driver")!!,
                startTime = Date(),
                endTime = null,
                startDate = null,
                endDate = null,
                breakTime = 0,
                startLocation = address,
                startLatitude = location?.latitude,
                startLongitude = location?.longitude,
                endLocation = null,
                endLatitude = null,
                endLongitude = null,
                startOdometer = odometer,
                endOdometer = null,
                carPlate = carPlate,
                type = EventType.WORK,
                isSynced = false
            )
            repository.insertWorkdayEvent(newWorkday)
            triggerSync()
        }
    }

    fun endWorkday(odometer: Int?) {
        viewModelScope.launch {
            activeWorkdayEvent?.let { workday ->
                val endTime = Date()
                val location = getCurrentLocation()
                val address = getAddressFromLocation(application, location)
                val updatedWorkday = workday.copy(
                    endTime = endTime,
                    endOdometer = odometer,
                    endLocation = address,
                    endLatitude = location?.latitude,
                    endLongitude = location?.longitude,
                    isSynced = false
                )
                repository.updateWorkdayEvent(updatedWorkday)

                val breaks = repository.getBreaksForWorkday(workday.localId).first()
                val totalBreakDuration = calculateBreakDuration(breaks, endTime.time)
                val netWorkDuration = endTime.time - workday.startTime.time - totalBreakDuration
                val eightHoursInMillis = 8 * 60 * 60 * 1000L
                if (netWorkDuration > eightHoursInMillis) {
                    val dailyOvertime = netWorkDuration - eightHoursInMillis
                    val currentCumulativeOvertime = prefs.getLong("cumulative_overtime", 0L)
                    val newCumulativeOvertime = currentCumulativeOvertime + dailyOvertime
                    prefs.edit().putLong("cumulative_overtime", newCumulativeOvertime).apply()
                    _overtime.value = newCumulativeOvertime
                }

                triggerSync()
            }
        }
    }

    fun startBreak() {
        viewModelScope.launch {
            activeWorkdayEvent?.let { workday ->
                val breakEvent = BreakEvent(workdayEventId = workday.localId, startTime = Date(), endTime = null, breakType = null, userId = this@MainViewModel.userId, isSynced = false)
                repository.insertBreakEvent(breakEvent)
                triggerSync()
            }
        }
    }

    fun endBreak() {
        viewModelScope.launch {
            activeBreakEvent?.let { breakEvent ->
                val updatedBreak = breakEvent.copy(endTime = Date(), isSynced = false)
                repository.updateBreakEvent(updatedBreak)
                triggerSync()
            }
        }
    }

    fun recordRefuel(odometer: Int, fuelType: String, fuelAmount: Double, paymentMethod: String, carPlate: String, value: Double?) {
        viewModelScope.launch {
            val location = getCurrentLocation()
            val address = getAddressFromLocation(application, location)
            val refuel = RefuelEvent(
                userId = userId,
                timestamp = Date(),
                odometer = odometer,
                fuelType = fuelType,
                fuelAmount = fuelAmount,
                paymentMethod = paymentMethod,
                location = address,
                latitude = location?.latitude,
                longitude = location?.longitude,
                carPlate = carPlate,
                value = value,
                isSynced = false
            )
            repository.insertRefuelEvent(refuel)
            triggerSync()
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
                    role = prefs.getString("user_role", "driver")!!,
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
                    type = eventType,
                    isSynced = false
                )
                repository.insertWorkdayEvent(newAbsence)
                triggerSync()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error recording absence", e)
            }
        }
    }

    fun insertManualWorkday(startTime: Date, endTime: Date?, startLocation: String?, endLocation: String?, carPlate: String?, startOdometer: Int?, endOdometer: Int?, breakTime: Int, type: EventType) {
        viewModelScope.launch {
            val newWorkday = WorkdayEvent(
                userId = userId,
                role = prefs.getString("user_role", "driver")!!,
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
                type = type,
                isSynced = false
            )
            repository.insertWorkdayEvent(newWorkday)
            triggerSync()
        }
    }

    fun loadWorkdayForEdit(id: Long) {
        viewModelScope.launch {
            _editingEvent.value = repository.getWorkdayEventById(id)
        }
    }

    fun updateWorkday(event: WorkdayEvent) {
        viewModelScope.launch {
            repository.updateWorkdayEvent(event.copy(isSynced = false))
            clearEditingEvent()
            triggerSync()
        }
    }

    fun loadLoadingForEdit(id: Long) {
        viewModelScope.launch {
            _editingEvent.value = repository.getLoadingEventById(id)
        }
    }

    fun updateLoading(event: LoadingEvent) {
        viewModelScope.launch {
            repository.updateLoadingEvent(event.copy(isSynced = false))
            clearEditingEvent()
            triggerSync()
        }
    }

    fun loadRefuelForEdit(id: Long) {
        viewModelScope.launch {
            _editingEvent.value = repository.getRefuelEventById(id)
        }
    }

    fun updateRefuel(event: RefuelEvent) {
        viewModelScope.launch {
            repository.updateRefuelEvent(event.copy(isSynced = false))
            clearEditingEvent()
            triggerSync()
        }
    }

    fun deleteWorkday(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkdayEventById(id)
            triggerSync()
        }
    }

    fun deleteRefuel(id: Long) {
        viewModelScope.launch {
            repository.deleteRefuelEventById(id)
            triggerSync()
        }
    }

    fun deleteLoading(id: Long) {
        viewModelScope.launch {
            repository.deleteLoadingEventById(id)
            triggerSync()
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                repository.deleteAllData()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting all data", e)
            }
        }
    }

    fun clearEditingEvent() {
        _editingEvent.value = null
    }

    fun clearWorkdayResults() {
        _workdayQueryResults.value = emptyList()
    }

    fun clearRefuelResults() {
        _refuelQueryResults.value = emptyList()
    }

    fun queryWorkdays(startDate: String, endDate: String, carPlate: String?) {
        viewModelScope.launch {
            if (userId == "unknown_user") return@launch
            synchronizeAndWait()
            val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

            val startCal = Calendar.getInstance().apply {
                if (startDate.isNotBlank()) {
                    time = dateFormat.parse(startDate)!!
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            val endCal = Calendar.getInstance().apply {
                if (endDate.isNotBlank()) {
                    time = dateFormat.parse(endDate)!!
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }

            val start = if (startDate.isNotBlank()) startCal.time else null
            val end = if (endDate.isNotBlank()) endCal.time else null

            val workdayEvents = repository.getAllWorkdayEvents(userId).firstOrNull() ?: emptyList<WorkdayEvent>()

            val filteredWorkdays = workdayEvents.filter<WorkdayEvent> { event ->
                val eventDate = event.startTime
                val carPlateMatch = carPlate.isNullOrBlank() || event.carPlate?.contains(carPlate, ignoreCase = true) == true
                val dateMatch = (start == null || !eventDate.before(start)) && (end == null || !eventDate.after(end))
                dateMatch && carPlateMatch
            }
            _workdayQueryResults.value = filteredWorkdays
        }
    }

    fun queryRefuels(startDate: String, endDate: String, carPlate: String?) {
        viewModelScope.launch {
            if (userId == "unknown_user") return@launch
            synchronizeAndWait()
            val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

            val startCal = Calendar.getInstance().apply {
                if (startDate.isNotBlank()) {
                    time = dateFormat.parse(startDate)!!
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            val endCal = Calendar.getInstance().apply {
                if (endDate.isNotBlank()) {
                    time = dateFormat.parse(endDate)!!
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }

            val start = if (startDate.isNotBlank()) startCal.time else null
            val end = if (endDate.isNotBlank()) endCal.time else null

            val refuelEvents = repository.getAllRefuelEvents(userId).firstOrNull() ?: emptyList<RefuelEvent>()

            val filteredRefuels = refuelEvents.filter<RefuelEvent> { event ->
                val eventDate = event.timestamp
                val carPlateMatch = carPlate.isNullOrBlank() || event.carPlate.contains(carPlate, ignoreCase = true)
                val dateMatch = (start == null || !eventDate.before(start)) && (end == null || !eventDate.after(end))
                dateMatch && carPlateMatch
            }
            _refuelQueryResults.value = filteredRefuels
        }
    }

    private suspend fun synchronizeAndWait() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueue(syncRequest)
        workManager.getWorkInfoByIdFlow(syncRequest.id)
            .filter { it.state.isFinished }
            .first()
    }

    private fun triggerSync() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueue(syncRequest)
    }

    fun synchronizeWithServer() {
        triggerSync()
    }

    fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun loadRecentEvents() {
        viewModelScope.launch {
            if (userId == "unknown_user") return@launch
            val allEvents = mutableListOf<Any>()
            allEvents.addAll(repository.getRecentWorkdayEvents(userId))
            allEvents.addAll(repository.getRecentRefuelEvents(userId))
            allEvents.addAll(repository.getRecentLoadingEvents(userId))
            _recentEvents.value = allEvents.sortedByDescending { event ->
                when (event) {
                    is WorkdayEvent -> event.startTime
                    is RefuelEvent -> event.timestamp
                    is LoadingEvent -> event.startTime
                    else -> Date(0)
                }
            }
        }
    }

    fun generateSummary(context: Context) {}

    private suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                continuation.resume(location)
            }
        }
    }

    private fun getAddressFromLocation(context: Context, location: Location?): String? {
        if (location == null) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.getAddressLine(0)
        } catch (e: IOException) {
            null
        }
    }
}
