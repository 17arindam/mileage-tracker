package com.example.mileagetracker.data.dao


import androidx.room.*
import com.example.mileagetracker.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Query("SELECT * FROM track_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    fun getTrackPointsByRouteId(routeId: String): Flow<List<TrackPoint>>

    @Insert
    suspend fun insertTrackPoint(trackPoint: TrackPoint)

    @Query("DELETE FROM track_points WHERE routeId = :routeId")
    suspend fun deleteTrackPointsByRouteId(routeId: String)

    @Query("SELECT * FROM track_points ORDER BY timestamp DESC")
    fun getAllTrackPoints(): Flow<List<TrackPoint>>
}