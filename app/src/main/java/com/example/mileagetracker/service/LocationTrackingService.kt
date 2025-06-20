package com.example.mileagetracker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.mileagetracker.MainActivity
import com.example.mileagetracker.R
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.data.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
        const val CHANNEL_ID = "location_tracking_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val EXTRA_ROUTE_ID = "EXTRA_ROUTE_ID"
        private const val WAKE_LOCK_TAG = "MileageTracker:LocationTrackingWakeLock"
        private const val WAKE_LOCK_TIMEOUT = 60 * 60 * 1000L // 1 hour
    }

    @Inject
    lateinit var locationRepository: LocationRepository

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Use a separate scope that won't be cancelled
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentRouteId: String? = null
    private var isServiceTracking = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "Location received: ${location.latitude}, ${location.longitude}")
                    handleLocationUpdate(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID)
                if (routeId != null) {
                    startLocationTracking(routeId)
                } else {
                    Log.e(TAG, "Route ID is null, cannot start tracking")
                    stopSelf()
                }
            }
            ACTION_STOP_TRACKING -> {
                stopLocationTracking()
            }
            else -> {
                // Handle service restart - check if we should resume tracking
                Log.d(TAG, "Service restarted, checking for active tracking")
                checkAndResumeTracking()
            }
        }

        // Return START_STICKY to restart service if killed by system
        return START_STICKY
    }



    private fun checkAndResumeTracking() {
        try {
            // Use runBlocking to ensure this completes before service continues
            runBlocking {
                val activeTrack = locationRepository.getActiveTrack().first()
                if (activeTrack != null && activeTrack.isTracking) {
                    Log.d(TAG, "Resuming tracking for route: ${activeTrack.routeId}")
                    startLocationTracking(activeTrack.routeId)
                } else {
                    Log.d(TAG, "No active tracking found, stopping service")
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active tracking", e)
            // Don't stop service immediately, try again later
            serviceScope.launch {
                kotlinx.coroutines.delay(5000) // Wait 5 seconds
                checkAndResumeTracking()
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            releaseWakeLock() // Release any existing wake lock first

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT)
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun startLocationTracking(routeId: String) {
        Log.d(TAG, "Starting location tracking for route: $routeId")

        currentRouteId = routeId
        isServiceTracking = true

        // Start foreground service FIRST to avoid ANR
        startForeground(NOTIFICATION_ID, createNotification())

        // Then acquire wake lock
        acquireWakeLock()

        // Start location updates
        startLocationUpdates()

        // Get the last known location from track points for distance calculation

    }

    private fun stopLocationTracking() {
        Log.d(TAG, "Stopping location tracking")

        isServiceTracking = false
        currentRouteId = null


        // Stop location updates
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Location updates stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }

        // Release wake lock
        releaseWakeLock()

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        // More conservative location request for better battery life
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // 10 seconds
            .setMinUpdateIntervalMillis(5000L) // 5 seconds minimum
            .setMaxUpdateDelayMillis(15000L) // 15 seconds maximum delay
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "General exception when requesting location updates", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val routeId = currentRouteId ?: return

        if (!isServiceTracking) {
            Log.w(TAG, "Received location update but service is not tracking")
            return
        }

        Log.d(TAG, "Handling location update for route: $routeId")

        serviceScope.launch {
            try {
                // Create track point
                val trackPoint = TrackPoint(
                    routeId = routeId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = location.speed,
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis()
                )

                // Insert track point
                locationRepository.insertTrackPoint(trackPoint)
                Log.d(TAG, "Track point saved")

                // Update notification
                updateNotification(location)





            } catch (e: Exception) {
                Log.e(TAG, "Error handling location update", e)
            }
        }
    }

    private fun updateNotification(location: Location) {
        val notification = createNotification(location)
        val notificationManager = getSystemService(NotificationManager::class.java)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for location tracking service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(location: Location? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (location != null) {
            "Tracking: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
        } else {
            "Tracking your location in background"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mileage Tracking Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.direction)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setShowWhen(false)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Tracking",
                stopPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        // Clean up resources
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }

        releaseWakeLock()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed - service will continue running")

        // Just log that task was removed, don't stop the service
        // The service should continue running with the foreground notification
        super.onTaskRemoved(rootIntent)
    }
}