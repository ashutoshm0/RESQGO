package com.example.resqgo.data.repository

import com.example.resqgo.data.local.RideEvent
import com.example.resqgo.data.local.RideEventDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log

class RideRepository(private val rideEventDao: RideEventDao) {

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e("RideRepository", "Failed to initialize Firebase", e)
        null
    }
    
    private val EVENTS_COLLECTION = "crash_events"

    /** Cloud sync must never outlive an emergency. */
    private val FIRESTORE_TIMEOUT_MS = 10_000L

    // Retrieve all events for history log
    val allEvents: Flow<List<RideEvent>> = rideEventDao.getAllEventsFlow()

    // Insert a new event (e.g. from SOSManager)
    suspend fun logEvent(
        latitude: Double, longitude: Double,
        eventType: String,
        address: String = "",
        smsSent: Boolean = false,
        contactsNotified: Int = 0,
        durationMs: Long = 0L,
        distanceKm: Float = 0f
    ) {
        val event = RideEvent(
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            eventType = eventType,
            isSynced = false,
            address = address,
            smsSent = smsSent,
            contactsNotified = contactsNotified,
            durationMs = durationMs,
            distanceKm = distanceKm
        )
        // The local write is the part that must succeed. Cloud sync is
        // best-effort and is deliberately NOT awaited here — an unreachable
        // Firestore used to block this call indefinitely, which stalled the
        // SOS coroutine before it could place the emergency phone call.
        rideEventDao.insertEvent(event)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { syncPendingEvents() }
        }
    }

    // Try to sync unsynced events to Firestore
    suspend fun syncPendingEvents() {
        val pendingEvents = rideEventDao.getUnsyncedEvents()
        
        if (firestore == null) {
            Log.w("RideRepository", "Firestore is null, skipping sync")
            return
        }
        
        for (event in pendingEvents) {
            try {
                // Upload to Firestore
                val eventMap = hashMapOf(
                    "timestamp" to event.timestamp,
                    "latitude" to event.latitude,
                    "longitude" to event.longitude,
                    "eventType" to event.eventType,
                    "status" to "NEW_ALERT",
                    "address" to event.address,
                    "smsSent" to event.smsSent,
                    "contactsNotified" to event.contactsNotified,
                    "durationMs" to event.durationMs,
                    "distanceKm" to event.distanceKm
                )
                
                // Use the timestamp as the document ID for uniqueness (or let Firebase auto-generate)
                // Firestore queues writes offline and the task only completes
                // once the server acknowledges, so it needs a hard deadline.
                val acked = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                    firestore.collection(EVENTS_COLLECTION).add(eventMap).await()
                }
                if (acked == null) {
                    Log.w("RideRepository", "Firestore sync timed out; will retry later")
                    continue
                }
                
                Log.d("RideRepository", "Successfully synced event: ${event.timestamp}")
                
                // If successful, mark as synced in local Room DB
                val updatedEvent = event.copy(isSynced = true)
                rideEventDao.updateEvent(updatedEvent)
                
            } catch (e: Exception) {
                Log.e("RideRepository", "Failed to sync event to Firebase", e)
                // If network fails, leave isSynced = false to retry later
            }
        }
    }
}
