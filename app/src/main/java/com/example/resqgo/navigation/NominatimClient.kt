package com.example.resqgo.navigation

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.osmdroid.util.GeoPoint

object NominatimClient {
    private const val TAG = "NominatimClient"
    private val client = OkHttpClient()

    /**
     * Searches for a place using OpenStreetMap's Nominatim service.
     * Required header: User-Agent to avoid 403 blocks.
     */
    suspend fun searchPlace(query: String): GeoPoint? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search?q=${Uri.encode(query)}&format=json&limit=1"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RESQGO-App/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Nominatim failed: ${response.code}")
                return@withContext null
            }

            val bodyStr = response.body?.string() ?: return@withContext null
            val array = JSONArray(bodyStr)

            if (array.length() == 0) return@withContext null

            val result = array.getJSONObject(0)
            val lat = result.getString("lat").toDouble()
            val lon = result.getString("lon").toDouble()

            return@withContext GeoPoint(lat, lon)
        } catch (e: Exception) {
            Log.e(TAG, "Nominatim exception", e)
            return@withContext null
        }
    }
}
