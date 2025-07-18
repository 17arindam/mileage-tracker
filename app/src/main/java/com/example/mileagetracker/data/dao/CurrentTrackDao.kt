    package com.example.mileagetracker.data.dao

    import androidx.room.*
    import com.example.mileagetracker.data.model.CurrentTrack
    import kotlinx.coroutines.flow.Flow

    @Dao
    interface CurrentTrackDao {
        @Query("SELECT * FROM current_track WHERE isTracking = 1 LIMIT 1")
        fun getActiveTrack(): Flow<CurrentTrack?>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertCurrentTrack(currentTrack: CurrentTrack)

        @Update
        suspend fun updateCurrentTrack(currentTrack: CurrentTrack)

        @Query("UPDATE current_track SET endLatitude = :endLat, endLongitude = :endLng, endTime = :endTime, isTracking = 0 WHERE routeId = :routeId")
        suspend fun stopTracking(routeId: String, endLat: Double, endLng: Double, endTime: Long)

        @Query("SELECT * FROM current_track ORDER BY startTime DESC")
        fun getAllTracks(): Flow<List<CurrentTrack>>

        @Query("DELETE FROM current_track WHERE routeId = :routeId")
        suspend fun deleteTrackByRouteId(routeId: String)
    }