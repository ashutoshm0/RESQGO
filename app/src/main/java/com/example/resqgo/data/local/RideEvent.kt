package com.example.resqgo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ride_events")
data class RideEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val eventType: String, // e.g., "CRASH", "FALL"
    val isSynced: Boolean = false, // false means it needs to be uploaded to Firestore
    val address: String = "",
    val smsSent: Boolean = false,
    val contactsNotified: Int = 0,
    val durationMs: Long = 0L,
    val distanceKm: Float = 0f
)
