package com.example.id.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.id.R
import com.example.id.data.AppRepository
import com.example.id.util.LocationProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject
    lateinit var repository: AppRepository

    @Inject
    lateinit var locationProvider: LocationProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var speedCheckRunnable: Runnable? = null

    private var belowSpeedThresholdTime: Long? = null
    private var isBreakActive = false
    private var isWorkdayActive = false
    private var currentUserId: String? = null

    companion object {
        const val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_OR_RESUME_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Tracking"
        const val NOTIFICATION_ID = 1
        const val SPEED_THRESHOLD_KMH = 20
        const val SPEED_CHECK_DELAY_MS = 2 * 60 * 1000L // 2 minutes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    runBlocking { // Blocking to get the user ID before proceeding
                        currentUserId = repository.getActiveWorkdayEvent().first()?.userId
                    }
                    if (currentUserId != null) {
                        startForegroundService()
                        observeLocation()
                        observeWorkdayAndBreakStatus()
                    } else {
                        Log.e("TrackingService", "Could not start service, no active workday or user ID.")
                        stopSelf()
                    }
                }
                ACTION_STOP_SERVICE -> {
                    stopService()
                }
            }
        }
        return START_STICKY
    }

    private fun observeLocation() {
        locationProvider.currentLocation
            .onEach { location ->
                val speedKmh = location.speed * 3.6
                if (isWorkdayActive && !isBreakActive) {
                    if (speedKmh < SPEED_THRESHOLD_KMH) {
                        if (belowSpeedThresholdTime == null) {
                            belowSpeedThresholdTime = System.currentTimeMillis()
                            startSpeedCheck()
                        }
                    } else {
                        belowSpeedThresholdTime = null
                        cancelSpeedCheck()
                        endLoadingIfNeeded()
                    }
                }
            }
            .catch { e -> Log.e("TrackingService", "Error observing location", e) }
            .launchIn(serviceScope)
    }

    private fun observeWorkdayAndBreakStatus() {
        currentUserId?.let {
            repository.getActiveWorkdayEvent(it)
                .onEach { workday -> isWorkdayActive = workday != null && workday.endTime == null }
                .launchIn(serviceScope)

            repository.getActiveBreakEvent(it)
                .onEach { breakEvent -> isBreakActive = breakEvent != null && breakEvent.endTime == null }
                .launchIn(serviceScope)
        }
    }

    private fun startSpeedCheck() {
        speedCheckRunnable = Runnable {
            belowSpeedThresholdTime?.let {
                startLoadingIfNeeded(it)
            }
        }
        handler.postDelayed(speedCheckRunnable!!, SPEED_CHECK_DELAY_MS)
    }

    private fun cancelSpeedCheck() {
        speedCheckRunnable?.let { handler.removeCallbacks(it) }
        speedCheckRunnable = null
    }

    private fun startLoadingIfNeeded(startTime: Long) {
        val intent = Intent("com.example.id.ACTION_START_LOADING").apply {
            putExtra("START_TIME", startTime)
        }
        sendBroadcast(intent)
    }

    private fun endLoadingIfNeeded() {
        val intent = Intent("com.example.id.ACTION_END_LOADING")
        sendBroadcast(intent)
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tracking Service")
            .setContentText("Monitoring location and speed.")
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopService() {
        cancelSpeedCheck()
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
