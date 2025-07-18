package com.example.mileagetracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.model.SavedTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedTrack(savedTrack: SavedTrack)

    @Query("SELECT * FROM saved_tracks ORDER BY createdAt DESC")
    fun getAllSavedTracks(): Flow<List<SavedTrack>>

    @Query("DELETE FROM saved_tracks WHERE routeId = :routeId")
    suspend fun deleteSavedTrack(routeId: String)

    @Query("SELECT * FROM saved_tracks WHERE routeId = :routeId")
    suspend fun getSavedTrackById(routeId: String): SavedTrack?
}