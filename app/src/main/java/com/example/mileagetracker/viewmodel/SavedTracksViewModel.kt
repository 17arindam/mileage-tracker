package com.example.mileagetracker.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.model.SavedTrack
import com.example.mileagetracker.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedTracksViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SavedTracksViewModel"
    }

    private val _savedTracks = MutableStateFlow<List<SavedTrack>>(emptyList())
    val savedTracks: StateFlow<List<SavedTrack>> = _savedTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSavedTracks()
    }

    private fun loadSavedTracks() {
        Log.d(TAG, "Loading saved tracks")
        viewModelScope.launch {
            try {
                locationRepository.getAllSavedTracks().collect { tracks ->
                    Log.d(TAG, "Loaded ${tracks.size} saved tracks")
                    _savedTracks.value = tracks
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved tracks", e)
                _isLoading.value = false
            }
        }
    }

    fun deleteTrack(routeId: String) {
        Log.d(TAG, "Deleting track with ID: $routeId")
        viewModelScope.launch {
            try {
                // Delete the saved track
                locationRepository.deleteSavedTrack(routeId)

                // Also delete associated track points
                locationRepository.deleteTrackPointsByRouteId(routeId)

                Log.d(TAG, "Track deleted successfully: $routeId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting track: $routeId", e)
            }
        }
    }
}