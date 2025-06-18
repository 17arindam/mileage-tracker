package com.example.mileagetracker.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LocationMapViewModel @Inject constructor(
    private val locationRepository: LocationRepository
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

    init {
        Log.d(TAG, "ViewModel initialized")
        observeActiveTrack()
    }

    private fun observeActiveTrack() {
        Log.d(TAG, "Starting to observe active track")
        viewModelScope.launch {
            locationRepository.getActiveTrack().collect { track ->
                Log.d(TAG, "Active track changed: ${track?.routeId}, isTracking: ${track?.isTracking}")

                _activeTrack.value = track
                _isTracking.value = track?.isTracking == true
                _currentRouteId.value = track?.routeId

                // Cancel previous track points collection
                trackPointsJob?.cancel()

                if (track?.routeId != null && track.isTracking) {
                    Log.d(TAG, "Starting to collect track points for route: ${track.routeId}")
                    // Start new collection for track points
                    trackPointsJob = viewModelScope.launch {
                        locationRepository.getTrackPointsByRouteId(track.routeId).collect { points ->
                            Log.d(TAG, "Track points updated for route ${track.routeId}: ${points.size} points")
                            _trackPoints.value = points

                            // Set last location to the most recent track point if available
                            if (points.isNotEmpty()) {
                                val lastPoint = points.last()
                                lastLocation = Location("").apply {
                                    latitude = lastPoint.latitude
                                    longitude = lastPoint.longitude
                                }
                                Log.d(TAG, "Updated last location from track points: ${lastPoint.latitude}, ${lastPoint.longitude}")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No active tracking, clearing track points")
                    // Clear track points when not tracking
                    _trackPoints.value = emptyList()
                    lastLocation = null
                }
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
            _initialLocation.value=location
            Log.d(TAG, "Clearing track points before starting new track")
            _trackPoints.value = emptyList()

            Log.d(TAG, "Inserting new track into database")
            locationRepository.startTracking(currentTrack)

            _currentRouteId.value = routeId
            _isTracking.value = true

            // Set the starting location as the last location for distance calculation
            lastLocation = location
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


            trackPointsJob?.cancel()

            _isTracking.value = false
            _currentRouteId.value = null
            _trackPoints.value = emptyList()
            lastLocation = null

            Log.d(TAG, "Tracking stopped successfully for route: $routeId")
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

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, canceling track points job")
        trackPointsJob?.cancel()
    }
}