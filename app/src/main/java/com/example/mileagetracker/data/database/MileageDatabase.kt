package com.example.mileagetracker.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.mileagetracker.data.dao.CurrentTrackDao
import com.example.mileagetracker.data.dao.SavedTrackDao
import com.example.mileagetracker.data.dao.TrackPointDao
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.SavedTrack
import com.example.mileagetracker.data.model.TrackPoint


@Database(
    entities = [TrackPoint::class, CurrentTrack::class, SavedTrack::class],
    version = 2, // Increment version
    exportSchema = false
)
abstract class MileageDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao
    abstract fun currentTrackDao(): CurrentTrackDao
    abstract fun savedTrackDao(): SavedTrackDao // Add this line
}