package com.example.mileagetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_track")
data class CurrentTrack(
    @PrimaryKey
    val routeId: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val startTime: Long,
    val distance: Double = 0.0,
    val endTime: Long? = null,
    val isTracking: Boolean = false
)
