package com.example.mileagetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_tracks")
data class SavedTrack(
    @PrimaryKey
    val routeId: String,
    val name: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val startTime: Long,
    val endTime: Long,
    val distance: Double,
    val duration: Long,
    val createdAt: Long = System.currentTimeMillis()
)