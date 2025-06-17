package com.example.mileagetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_points")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long
)
