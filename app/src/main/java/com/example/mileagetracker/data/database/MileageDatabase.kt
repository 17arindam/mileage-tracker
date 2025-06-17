package com.example.mileagetracker.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.mileagetracker.data.dao.CurrentTrackDao
import com.example.mileagetracker.data.dao.TrackPointDao
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.TrackPoint


@Database(
    entities = [TrackPoint::class, CurrentTrack::class],
    version = 1,
    exportSchema = false
)
abstract class MileageDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao
    abstract fun currentTrackDao(): CurrentTrackDao
}