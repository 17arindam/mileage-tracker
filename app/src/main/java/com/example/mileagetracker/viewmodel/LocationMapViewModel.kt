package com.example.mileagetracker.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    init {
        observeActiveTrack()
    }

    private fun observeActiveTrack() {
        viewModelScope.launch {
            locationRepository.getActiveTrack().collect { track ->
                _activeTrack.value = track
                _isTracking.value = track?.isTracking == true
                _currentRouteId.value = track?.routeId

                // Load track points for active route
                track?.routeId?.let { routeId ->
                    locationRepository.getTrackPointsByRouteId(routeId).collect { points ->
                        _trackPoints.value = points
                        // Set last location to the most recent track point if available
                        if (points.isNotEmpty() && _isTracking.value) {
                            val lastPoint = points.last()
                            lastLocation = Location("").apply {
                                latitude = lastPoint.latitude
                                longitude = lastPoint.longitude
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateLocation(location: Location) {
        _currentLocation.value = location

        // If tracking is active, save the location point and update distance
        if (_isTracking.value && _currentRouteId.value != null) {
            saveTrackPointAndUpdateDistance(location)
        }
    }

    fun updateAzimuth(azimuth: Float) {
        _currentAzimuth.value = azimuth
    }

    fun startTracking(location: Location) {
//        if(activeTrack!=null){
//            locationRepository.deleteTrack(activeTrack.value!!.routeId )
//        }

        val routeId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()


        val currentTrack = CurrentTrack(
            routeId = routeId,
            startLatitude = location.latitude,
            startLongitude = location.longitude,
            startTime = currentTime,
            isTracking = true,
            distance = 0.0,
        )

        viewModelScope.launch {
            _trackPoints.value = emptyList()
            locationRepository.startTracking(currentTrack)
            _currentRouteId.value = routeId
            _isTracking.value = true
            // Set the starting location as the last location for distance calculation
            lastLocation = location
        }

    }

    fun stopTracking() {
        val location = _currentLocation.value ?: return
        val routeId = _currentRouteId.value ?: return
        val currentTime = System.currentTimeMillis()

        viewModelScope.launch {
            locationRepository.stopTracking(
                routeId = routeId,
                endLat = location.latitude,
                endLng = location.longitude,
                endTime = currentTime
            )
            _isTracking.value = false
            _currentRouteId.value = null
            lastLocation = null // Reset last location
        }
    }

    private fun saveTrackPointAndUpdateDistance(location: Location) {
        val routeId = _currentRouteId.value ?: return
        val activeTrack = _activeTrack.value ?: return

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

            // Calculate distance if we have a previous location
            val distanceToAdd = lastLocation?.let { prevLocation ->
                prevLocation.distanceTo(location).toDouble()
            } ?: 0.0

            // Update the current track with new distance
            val updatedDistance = activeTrack.distance + distanceToAdd
            val updatedTrack = activeTrack.copy(distance = updatedDistance)

            locationRepository.updateCurrentTrack(updatedTrack)

            // Update the last location for next calculation
            lastLocation = location
        }
    }

    private fun saveTrackPoint(location: Location) {
        val routeId = _currentRouteId.value ?: return

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
        }
    }

    fun getTrackPointsForRoute(routeId: String) {
        viewModelScope.launch {
            locationRepository.getTrackPointsByRouteId(routeId).collect { points ->
                _trackPoints.value = points
            }
        }
    }
}