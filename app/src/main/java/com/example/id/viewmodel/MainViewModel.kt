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
import com.example.id.USER_NAME_KEY
import com.example.id.data.AppRepository
import com.example.id.data.entities.RefuelEvent
import com.example.id.data.entities.WorkdayEvent
import com.example.id.repository.AuthRepository
import com.example.id.util.ApiService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
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
    private val utilApiService: ApiService,
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    private val userId: String = prefs.getString(USER_NAME_KEY, "unknown_user")!!
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)
    private val _recentEvents = MutableStateFlow<List<Any>>(emptyList())
    val recentEvents: StateFlow<List<Any>> = _recentEvents.asStateFlow()


    val isWorkdayStarted: StateFlow<Boolean> = repository.getActiveWorkdayEvent(userId).map { it != null }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)
    val isBreakStarted: StateFlow<Boolean> = repository.getActiveBreakEvent(userId).map { it != null }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)
    val isloadingStarted: StateFlow<Boolean> = repository.getActiveLoadingEvent(userId).map { it != null }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)
    private val _workDuration = MutableStateFlow(0L)
    val workDuration: StateFlow<Long> = _workDuration.asStateFlow()
    private val _breakDuration = MutableStateFlow(0L)
    val breakDuration: StateFlow<Long> = _breakDuration.asStateFlow()
    private val _overtime = MutableStateFlow(prefs.getLong("cumulative_overtime", 0L))
    val overtime: StateFlow<Long> = _overtime.asStateFlow()
    private var activeWorkdayEvent: WorkdayEvent? = null

    init {
        viewModelScope.launch {
            repository.getActiveWorkdayEvent(userId).collect { event ->
                activeWorkdayEvent = event
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            try {
                val response = authRepository.login(username, password)
                if (response.isSuccessful && response.body() != null) {
                    authRepository.saveToken(response.body()!!.token)
                    _loginState.value = LoginUiState.Success
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown login error"
                    _loginState.value = LoginUiState.Error(errorBody)
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun validateToken() {
        viewModelScope.launch {
            val token = authRepository.getToken()
            if (token == null) {
                _loginState.value = LoginUiState.Error("No token found")
                return@launch
            }

            try {
                val response = authRepository.validateToken(token)
                if (response.isSuccessful && response.body()?.valid == true) {
                    _loginState.value = LoginUiState.Success
                } else {
                    authRepository.clearToken()
                    _loginState.value = LoginUiState.Error("Invalid token")
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun logout() {
        authRepository.clearToken()
        _loginState.value = LoginUiState.Idle
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
                type = com.example.id.data.entities.EventType.WORK
            )
            repository.insertWorkdayEvent(newWorkday)
            try {
                val response = utilApiService.postWorkday(newWorkday)
                if (!response.isSuccessful) {
                    Log.e("MainViewModel", "Failed to send workday: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Exception when sending workday", e)
            }
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
                    endLongitude = location?.longitude
                )
                repository.updateWorkdayEvent(updatedWorkday)

                try {
                    val response = utilApiService.postWorkday(updatedWorkday)
                    if (!response.isSuccessful) {
                        Log.e("MainViewModel", "Failed to send workday end: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Exception when sending workday end", e)
                }
            }
        }
    }

    fun recordRefuel(odometer: Int, fuelType: String, fuelAmount: Double, paymentMethod: String, carPlate: String) {
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
                carPlate = carPlate
            )
            repository.insertRefuelEvent(refuel)
            try {
                val response = utilApiService.postRefuel(refuel)
                if (!response.isSuccessful) {
                    Log.e("MainViewModel", "Failed to send refuel event: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Exception when sending refuel event", e)
            }
        }
    }

    fun loadRecentEvents() {
        viewModelScope.launch {
            val allEvents = mutableListOf<Any>()
            allEvents.addAll(repository.getRecentWorkdayEvents(userId))
            allEvents.addAll(repository.getRecentRefuelEvents(userId))
            allEvents.addAll(repository.getRecentLoadingEvents(userId))
            _recentEvents.value = allEvents.sortedByDescending { event ->
                when (event) {
                    is WorkdayEvent -> event.startTime
                    is RefuelEvent -> event.timestamp
                    is com.example.id.data.entities.LoadingEvent -> event.startTime
                    else -> Date(0)
                }
            }
        }
    }

    fun deleteWorkdayEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteWorkdayEvent(id)
            loadRecentEvents()
        }
    }

    fun deleteRefuelEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteRefuelEvent(id)
            loadRecentEvents()
        }
    }

    fun deleteLoadingEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteLoadingEvent(id)
            loadRecentEvents()
        }
    }

    fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

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
