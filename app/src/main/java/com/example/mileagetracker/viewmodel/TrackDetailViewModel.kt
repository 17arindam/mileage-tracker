package com.example.mileagetracker.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.model.SavedTrack
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TrackDetailViewModel"
    }

    private val _savedTrack = MutableStateFlow<SavedTrack?>(null)
    val savedTrack: StateFlow<SavedTrack?> = _savedTrack.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadTrackData(routeId: String) {
        Log.d(TAG, "Loading track data for route: $routeId")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Load saved track details
                val track = locationRepository.getSavedTrackById(routeId)
                if (track != null) {
                    _savedTrack.value = track
                    Log.d(TAG, "Loaded saved track: ${track.name}")

                    // Load track points
                    locationRepository.getTrackPointsByRouteId(routeId).collect { points ->
                        Log.d(TAG, "Loaded ${points.size} track points for route: $routeId")
                        _trackPoints.value = points
                        _isLoading.value = false
                    }
                } else {
                    Log.w(TAG, "No saved track found for route: $routeId")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading track data for route: $routeId", e)
                _isLoading.value = false
            }
        }
    }
}