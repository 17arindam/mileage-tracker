package com.example.mileagetracker.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.data.repository.LocationRepository
import com.example.mileagetracker.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LocationMapViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "LocationMapViewModel"
    }

    private val _initialLocation = MutableStateFlow<Location?>(null)
    val initialLocation: StateFlow<Location?> = _initialLocation.asStateFlow()

    private val _finalLocation = MutableStateFlow<Location?>(null)
    val finalLocation: StateFlow<Location?> = _finalLocation.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _currentAzimuth = MutableStateFlow(0f)
    val currentAzimuth: StateFlow<Float> = _currentAzimuth.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentRouteId = MutableStateFlow<String?>(null)
    val currentRouteId: StateFlow<String?> = _currentRouteId.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

    private val _activeTrack = MutableStateFlow<CurrentTrack?>(null)
    val activeTrack: StateFlow<CurrentTrack?> = _activeTrack.asStateFlow()

    // Store the last location for distance calculation
    private var lastLocation: Location? = null

    // Job to manage track points collection
    private var trackPointsJob: Job? = null

    private val _lastCompletedTrack = MutableStateFlow<CurrentTrack?>(null)
    val lastCompletedTrack: StateFlow<CurrentTrack?> = _lastCompletedTrack.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialized")
        observeActiveTrack()
    }

    private fun observeActiveTrack() {
        Log.d(TAG, "Starting to observe active track")
        viewModelScope.launch {
            locationRepository.getActiveTrack().collect { track ->
                Log.d(
                    TAG,
                    "Active track changed: ${track?.routeId}, isTracking: ${track?.isTracking}"
                )

                _activeTrack.value = track
                _isTracking.value = track?.isTracking == true
                _currentRouteId.value = track?.routeId

                // Cancel previous track points collection
                trackPointsJob?.cancel()

                if (track?.routeId != null && track.isTracking) {
                    Log.d(TAG, "Starting to collect track points for route: ${track.routeId}")
                    // Start new collection for track points
                    trackPointsJob = viewModelScope.launch {
                        locationRepository.getTrackPointsByRouteId(track.routeId)
                            .collect { points ->
                                Log.d(
                                    TAG,
                                    "Track points updated for route ${track.routeId}: ${points.size} points"
                                )
                                _trackPoints.value = points

                                // Set last location to the most recent track point if available
                                if (points.isNotEmpty()) {
                                    val lastPoint = points.last()
                                    lastLocation = Location("").apply {
                                        latitude = lastPoint.latitude
                                        longitude = lastPoint.longitude
                                    }
                                    Log.d(
                                        TAG,
                                        "Updated last location from track points: ${lastPoint.latitude}, ${lastPoint.longitude}"
                                    )
                                }
                            }
                    }
                }
                // DON'T clear track points here when not tracking - let them persist for summary
            }
        }
    }

    fun setTracking(value: Boolean) {
        _isTracking.value = value
    }

    fun updateLocation(location: Location) {
        _currentLocation.value = location
        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")

        // If tracking is active, save the location point and update distance
        if (_isTracking.value && _currentRouteId.value != null) {
            Log.d(TAG, "Tracking active, saving track point")
            saveTrackPointAndUpdateDistance(location)
        }
    }

    suspend fun saveTrackWithName(
        name: String, distance: Float, duration: Long,
        currentTrack: CurrentTrack
    ) {

        locationRepository.saveTripWithName(

            name = name,
            currentTrack = currentTrack,
            distance = distance.toDouble(),
            duration = duration
        )

    }

    fun updateAzimuth(azimuth: Float) {
        _currentAzimuth.value = azimuth
    }

    fun startTracking(location: Location) {
        Log.d(TAG, "Starting tracking at location: ${location.latitude}, ${location.longitude}")

        val routeId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()

        Log.d(TAG, "Generated new route ID: $routeId")

        val currentTrack = CurrentTrack(
            routeId = routeId,
            startLatitude = location.latitude,
            startLongitude = location.longitude,
            startTime = currentTime,
            isTracking = true,
            distance = 0.0,
        )

        viewModelScope.launch {
            _initialLocation.value = location
            _activeTrack.value = currentTrack
            Log.d(TAG, "Clearing track points before starting new track")
            _trackPoints.value = emptyList() // Clear here when starting new tracking
            lastLocation = null // Reset last location here

            Log.d(TAG, "Inserting new track into database")
            locationRepository.startTracking(currentTrack)

            _currentRouteId.value = routeId
            _isTracking.value = true

            // Set the starting location as the last location for distance calculation
            lastLocation = location

            // Start the foreground service
            startLocationTrackingService(routeId)

            Log.d(TAG, "Track started successfully with route ID: $routeId")
        }
    }

    fun stopTracking() {
        val location = _currentLocation.value ?: return
        val routeId = _currentRouteId.value ?: return
        val currentTime = System.currentTimeMillis()


        Log.d(TAG, "Stopping tracking for route: $routeId")

        viewModelScope.launch {

            locationRepository.stopTracking(
                routeId = routeId,
                endLat = location.latitude,
                endLng = location.longitude,
                endTime = currentTime
            )
            _lastCompletedTrack.value = activeTrack.value
            trackPointsJob?.cancel()

            _isTracking.value = false
            _currentRouteId.value = null
            // DON'T clear track points here - let them persist for the summary
            // _trackPoints.value = emptyList()
            // lastLocation = null

            // Stop the foreground service
            stopLocationTrackingService()

            Log.d(TAG, "Tracking stopped successfully for route: $routeId")
        }
    }

    private fun startLocationTrackingService(routeId: String) {
        Log.d(TAG, "Starting location tracking service for route: $routeId")

        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
            putExtra(LocationTrackingService.EXTRA_ROUTE_ID, routeId)
            Log.d(TAG, "Service intent extras: ${extras.toString()}")
        }

        try {
            // Use startForegroundService for API 26+ and startService for older versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
                Log.d(TAG, "Location tracking service started using startForegroundService")
            } else {
                context.startService(serviceIntent)
                Log.d(TAG, "Location tracking service started using startService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking service", e)
        }
    }

    private fun stopLocationTrackingService() {
        Log.d(TAG, "Stopping location tracking service")

        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }

        try {
            context.startService(serviceIntent)
            Log.d(TAG, "Location tracking service stop request sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location tracking service", e)
        }
    }

    private fun saveTrackPointAndUpdateDistance(location: Location) {
        val routeId = _currentRouteId.value ?: return
        val activeTrack = _activeTrack.value ?: return

        Log.d(TAG, "Saving track point for route: $routeId")

        val trackPoint = TrackPoint(
            routeId = routeId,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed,
            accuracy = location.accuracy,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            // Insert the track point
            locationRepository.insertTrackPoint(trackPoint)
            Log.d(TAG, "Track point inserted: ${trackPoint.latitude}, ${trackPoint.longitude}")

            // Calculate distance if we have a previous location
            val distanceToAdd = lastLocation?.let { prevLocation ->
                val distance = prevLocation.distanceTo(location).toDouble()
                Log.d(TAG, "Distance calculated: $distance meters")
                distance
            } ?: 0.0

            // Update the current track with new distance
            val updatedDistance = activeTrack.distance + distanceToAdd
            val updatedTrack = activeTrack.copy(distance = updatedDistance)

            Log.d(TAG, "Updating track distance: ${activeTrack.distance} -> $updatedDistance")
            locationRepository.updateCurrentTrack(updatedTrack)

            // Update the last location for next calculation
            lastLocation = location
        }
    }

    private fun saveTrackPoint(location: Location) {
        val routeId = _currentRouteId.value ?: return

        Log.d(TAG, "Saving simple track point for route: $routeId")

        val trackPoint = TrackPoint(
            routeId = routeId,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed,
            accuracy = location.accuracy,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            locationRepository.insertTrackPoint(trackPoint)
            Log.d(TAG, "Simple track point saved")
        }
    }

    fun getTrackPointsForRoute(routeId: String) {
        Log.d(TAG, "Manually getting track points for route: $routeId")
        viewModelScope.launch {
            locationRepository.getTrackPointsByRouteId(routeId).collect { points ->
                Log.d(TAG, "Manual track points collection: ${points.size} points")
                _trackPoints.value = points
            }
        }
    }

    fun checkAndResumeTrackingOnAppStart() {
        Log.d(TAG, "Checking for active tracking on app start")
        viewModelScope.launch {
            try {
                val activeTrack = locationRepository.getActiveTrack().first()
                if (activeTrack?.isTracking == true) {
                    Log.d(TAG, "Found active tracking on app start: ${activeTrack.routeId}")

                    // Update UI state to reflect ongoing tracking
                    _currentRouteId.value = activeTrack.routeId
                    _isTracking.value = true

                    // Get track points for the active route
                    getTrackPointsForRoute(activeTrack.routeId)

                    // The service should already be running, but ensure it's started
                    // This is a safety measure in case the service was killed
                    startLocationTrackingService(activeTrack.routeId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for active tracking", e)
            }
        }
    }

    fun clearTrackingData() {
        Log.d(TAG, "Clearing tracking data after summary")
        _trackPoints.value = emptyList()
        lastLocation = null
        _initialLocation.value = null
        _finalLocation.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, canceling track points job")
        trackPointsJob?.cancel()
    }
}