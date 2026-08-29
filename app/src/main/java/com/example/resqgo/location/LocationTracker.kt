package com.example.resqgo.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Handles all GPS operations for RESQGO:
 * - Continuous location updates during a ride (every 10s)
 * - High-accuracy one-shot location for SOS
 * - Reverse geocoding to get street address from coordinates
 */
class LocationTracker(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Breadcrumb trail recorded during the ride
    private val breadcrumbs = mutableListOf<RidePoint>()
    private var lastLocation: Location? = null
    private var isTracking = false

    // Ride location update: every 10 seconds, minimum 5m movement
    private val rideLocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10_000L        // interval 10 seconds
    ).apply {
        setMinUpdateDistanceMeters(5f)   // only update if moved 5m+
        setWaitForAccurateLocation(false)
    }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLocation = loc
            breadcrumbs.add(
                RidePoint(
                    lat       = loc.latitude,
                    lng       = loc.longitude,
                    altM      = loc.altitude.toFloat(),
                    speedKmh  = (loc.speed * 3.6f),
                    accuracyM = loc.accuracy,
                    timestamp = loc.time
                )
            )
            Log.d("LocationTracker", "📍 ${loc.latitude}, ${loc.longitude} | speed: ${loc.speed * 3.6f} km/h")
        }
    }

    // ── Start continuous tracking ──────────────────────────
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasLocationPermission()) {
            Log.w("LocationTracker", "Location permission not granted")
            return
        }
        if (isTracking) return
        breadcrumbs.clear()
        isTracking = true
        fusedClient.requestLocationUpdates(
            rideLocationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        Log.d("LocationTracker", "🟢 GPS tracking started")
    }

    // ── Stop continuous tracking ───────────────────────────
    fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        isTracking = false
        Log.d("LocationTracker", "🔴 GPS tracking stopped. ${breadcrumbs.size} points recorded.")
    }

    // ── Get the most recent location immediately ───────────
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    lastLocation = location ?: lastLocation
                    cont.resume(lastLocation)
                }
                .addOnFailureListener {
                    cont.resume(lastLocation) // fallback to last known
                }
        }
    }

    // ── Returns lastLocation instantly (non-suspend) ───────
    fun getLastLocation(): Location? = lastLocation

    // ── Breadcrumb trail ───────────────────────────────────
    fun getBreadcrumbs(): List<RidePoint> = breadcrumbs.toList()
    fun getTotalDistanceKm(): Float {
        if (breadcrumbs.size < 2) return 0f
        var total = 0f
        for (i in 1 until breadcrumbs.size) {
            val prev = breadcrumbs[i - 1]
            val curr = breadcrumbs[i]
            val results = FloatArray(1)
            Location.distanceBetween(prev.lat, prev.lng, curr.lat, curr.lng, results)
            total += results[0]
        }
        return total / 1000f  // convert to km
    }

    // ── Reverse geocoding ──────────────────────────────────
    /**
     * Converts (lat, lng) to a human-readable street address.
     * Uses Android's built-in Geocoder — NO API KEY NEEDED.
     * Returns formatted address like:
     * "MG Road, near Brigade Road, Bengaluru, Karnataka 560001"
     */
    fun getAddressFromLocation(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                formatAddress(addresses[0])
            } else {
                "%.5f, %.5f".format(lat, lng)
            }
        } catch (e: Exception) {
            Log.w("LocationTracker", "Reverse geocoding failed", e)
            "%.5f, %.5f".format(lat, lng)
        }
    }

    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()
        // Street number + name
        address.thoroughfare?.let { parts.add(it) }
        address.subLocality?.let { parts.add(it) }
        address.locality?.let { parts.add(it) }
        address.adminArea?.let { parts.add(it) }
        // Fallback if street not available
        if (parts.isEmpty()) {
            address.getAddressLine(0)?.let { parts.add(it) }
        }
        return parts.joinToString(", ")
    }

    // ── Helpers ────────────────────────────────────────────
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /** Builds a Google Maps URL from coordinates */
    fun toMapsUrl(lat: Double, lng: Double): String =
        "https://maps.google.com/?q=$lat,$lng"

    /** Builds a WhatsApp-ready location message */
    fun buildSOSMessage(riderName: String, lat: Double, lng: Double, address: String): String {
        val mapsLink = toMapsUrl(lat, lng)
        return buildString {
            append("🚨 [RESQGO ALERT]\n")
            append("*$riderName* may have been in an accident!\n\n")
            append("📍 *Location:* $address\n")
            append("🗺 *Maps:* $mapsLink\n\n")
            append("Please call immediately or contact emergency services (112).")
        }
    }
}

/** A single GPS point recorded during the ride */
data class RidePoint(
    val lat: Double,
    val lng: Double,
    val altM: Float,
    val speedKmh: Float,
    val accuracyM: Float,
    val timestamp: Long
)
