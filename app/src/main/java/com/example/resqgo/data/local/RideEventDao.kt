package com.example.resqgo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideEventDao {
    @Insert
    suspend fun insertEvent(event: RideEvent): Long

    @Query("SELECT * FROM ride_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<RideEvent>>

    @Query("SELECT * FROM ride_events WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedEvents(): List<RideEvent>

    @Query("SELECT * FROM ride_events WHERE eventType IN ('SOS_MANUAL', 'SOS_AUTO') ORDER BY timestamp DESC")
    fun getSOSEventsFlow(): Flow<List<RideEvent>>

    @Query("SELECT * FROM ride_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<RideEvent>

    @Update
    suspend fun updateEvent(event: RideEvent)
}
