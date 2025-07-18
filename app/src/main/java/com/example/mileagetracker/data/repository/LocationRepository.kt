package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.dao.CurrentTrackDao
import com.example.mileagetracker.data.dao.SavedTrackDao
import com.example.mileagetracker.data.dao.TrackPointDao
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.SavedTrack
import com.example.mileagetracker.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val trackPointDao: TrackPointDao,
    private val currentTrackDao: CurrentTrackDao,
    private val savedTrackDao: SavedTrackDao
) {

    suspend fun insertTrackPoint(trackPoint: TrackPoint) {
        trackPointDao.insertTrackPoint(trackPoint)
    }

    fun getTrackPointsByRouteId(routeId: String): Flow<List<TrackPoint>> {
        return trackPointDao.getTrackPointsByRouteId(routeId)
    }

    suspend fun deleteTrackPointsByRouteId(routeId: String) {
        trackPointDao.deleteTrackPointsByRouteId(routeId)
    }

    suspend fun saveTripWithName(
        routeId: String,
        name: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        startTime: Long,
        endTime: Long,
        distance: Double,
        duration: Long
    ) {
        val savedTrack = SavedTrack(
            routeId = routeId,
            name = name,
            startLatitude = startLat,
            startLongitude = startLng,
            endLatitude = endLat,
            endLongitude = endLng,
            startTime = startTime,
            endTime = endTime,
            distance = distance,
            duration = duration
        )
        savedTrackDao.insertSavedTrack(savedTrack)
    }

    fun getAllSavedTracks(): Flow<List<SavedTrack>> {
        return savedTrackDao.getAllSavedTracks()
    }

    suspend fun getSavedTrackById(routeId: String): SavedTrack? {
        return savedTrackDao.getSavedTrackById(routeId)
    }

    suspend fun deleteSavedTrack(routeId: String) {
        savedTrackDao.deleteSavedTrack(routeId)
    }

    fun getAllTrackPoints(): Flow<List<TrackPoint>> {
        return trackPointDao.getAllTrackPoints()
    }

    // CurrentTrack operations
    fun getActiveTrack(): Flow<CurrentTrack?> {
        return currentTrackDao.getActiveTrack()
    }

    suspend fun startTracking(currentTrack: CurrentTrack) {
        currentTrackDao.insertCurrentTrack(currentTrack)
    }

    suspend fun updateCurrentTrack(currentTrack: CurrentTrack) {
        currentTrackDao.updateCurrentTrack(currentTrack)
    }

    suspend fun stopTracking(routeId: String, endLat: Double, endLng: Double, endTime: Long) {
        currentTrackDao.stopTracking(routeId, endLat, endLng, endTime)
    }

    fun getAllTracks(): Flow<List<CurrentTrack>> {
        return currentTrackDao.getAllTracks()
    }

    suspend fun deleteTrack(trackId: String) {
        currentTrackDao.deleteTrackByRouteId(trackId)
    }
}