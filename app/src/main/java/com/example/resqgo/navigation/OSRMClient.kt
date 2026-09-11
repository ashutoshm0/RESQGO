package com.example.resqgo.navigation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

object OSRMClient {
    private const val TAG = "OSRMClient"
    private val client = OkHttpClient()

    data class RouteResult(
        val distanceMetres: Double,
        val durationSeconds: Double,
        val points: List<GeoPoint>
    )

    /**
     * Fetches a road route from OSRM's public demo server.
     * Coordinates must be passed in longitude,latitude order!
     */
    suspend fun getRoute(waypoints: List<GeoPoint>): RouteResult? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext null

        val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "OSRM failed: ${response.code}")
                return@withContext null
            }

            val bodyStr = response.body?.string() ?: return@withContext null
            val root = JSONObject(bodyStr)
            
            if (root.optString("code") != "Ok") {
                Log.e(TAG, "OSRM error: ${root.optString("code")}")
                return@withContext null
            }

            val routes = root.getJSONArray("routes")
            if (routes.length() == 0) return@withContext null

            val route = routes.getJSONObject(0)
            val distance = route.getDouble("distance")
            val duration = route.getDouble("duration")
            
            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            
            val points = mutableListOf<GeoPoint>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                val lng = coord.getDouble(0)
                val lat = coord.getDouble(1)
                points.add(GeoPoint(lat, lng))
            }

            return@withContext RouteResult(distance, duration, points)
        } catch (e: Exception) {
            Log.e(TAG, "OSRM exception", e)
            return@withContext null
        }
    }
}
