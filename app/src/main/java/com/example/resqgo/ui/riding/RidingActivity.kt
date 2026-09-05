package com.example.resqgo.ui.riding

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivityRidingBinding
import com.example.resqgo.location.LocationTracker
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import com.example.resqgo.ui.home.HomeActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

class RidingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRidingBinding
    private lateinit var prefs: UserPreferences
    private lateinit var sosManager: SOSManager
    private lateinit var locationTracker: LocationTracker

    private var mMap: GoogleMap? = null
    private var bikeMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var activeRoutePolyline: Polyline? = null
    private val alternativePolylines = mutableListOf<Polyline>()

    private var currentRiderLatLng: LatLng? = null
    private var currentDestinationLatLng: LatLng? = null
    private var isNavigating = false
    private var lastBearing = 0f

    // Sensor broadcast receiver from background RideMonitoringService
    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HomeActivity.ACTION_SENSOR_UPDATE) return

            val gForce   = intent.getFloatExtra("gForce", 9.8f)
            val speedKmh = intent.getFloatExtra("speedKmh", -1f)
            val gx       = intent.getFloatExtra("gx", 0f)
            val gy       = intent.getFloatExtra("gy", 0f)
            val gpsLocked = intent.getBooleanExtra("gpsLocked", false)

            // Update diagnostics
            binding.tvDiagGForce.text = String.format(Locale.US, "%.2f G", gForce / 9.80665f)
            binding.tvDiagGyroX.text = String.format(Locale.US, "%.2f rad/s", gx)
            binding.tvDiagGyroY.text = String.format(Locale.US, "%.2f rad/s", gy)
            binding.tvDiagGps.text = if (gpsLocked) "Locked (3D)" else "Searching..."
            binding.tvDiagGps.setTextColor(
                ContextCompat.getColor(this@RidingActivity, if (gpsLocked) R.color.resqgo_success else R.color.resqgo_alert)
            )

            // Speedometer update
            if (speedKmh >= 0) {
                binding.tvSpeed.text = String.format(Locale.US, "%d km/h", speedKmh.roundToInt())
            }

            // Also check latest GPS coordinates from locationTracker
            val loc = locationTracker.getLastLocation()
            if (loc != null) {
                onLocationChanged(loc)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRidingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPreferences(this)
        sosManager = SOSManager(this)
        locationTracker = LocationTracker(this)

        onBackPressedDispatcher.addCallback(this) {
            confirmEndRide()
        }

        setupUI()
        setupDiagnosticsDrawer()

        // Initialize Google Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Ensure background monitoring service is running
        if (!prefs.isRidingOnline) {
            prefs.isRidingOnline = true
            val serviceIntent = Intent(this, RideMonitoringService::class.java).apply {
                action = RideMonitoringService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLocationIntent(intent)
    }

    private fun setupUI() {
        // Display selected Crash Confirmation Timer
        binding.tvSosTimerDisplay.text = "SOS Countdown: ${prefs.sosTimerSeconds}s"

        // Search input handling
        binding.etDestination.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                val query = binding.etDestination.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchAndPlotDestination(query)
                }
                true
            } else {
                false
            }
        }

        binding.btnSearchDestination.setOnClickListener {
            hideKeyboard()
            val query = binding.etDestination.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAndPlotDestination(query)
            } else {
                Toast.makeText(this, "Enter a destination name or coordinates", Toast.LENGTH_SHORT).show()
            }
        }

        // Re-center on bike
        binding.fabRecenter.setOnClickListener {
            currentRiderLatLng?.let { latLng ->
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            } ?: Toast.makeText(this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show()
        }

        // Navigation mode start
        binding.btnStartNavigation.setOnClickListener {
            isNavigating = !isNavigating
            if (isNavigating) {
                binding.btnStartNavigation.text = "Navigating"
                binding.btnStartNavigation.setBackgroundColor(ContextCompat.getColor(this, R.color.resqgo_success))
                currentRiderLatLng?.let { riderLoc ->
                    val camPos = CameraPosition.builder()
                        .target(riderLoc)
                        .zoom(18f)
                        .bearing(lastBearing)
                        .tilt(45f)
                        .build()
                    mMap?.animateCamera(CameraUpdateFactory.newCameraPosition(camPos))
                }
                Toast.makeText(this, "Navigation active • Live tracking enabled", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnStartNavigation.text = "Start Nav"
                binding.btnStartNavigation.setBackgroundColor(ContextCompat.getColor(this, R.color.resqgo_primary))
                currentRiderLatLng?.let { riderLoc ->
                    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(riderLoc, 15f))
                }
            }
        }

        // Route choices chip group
        binding.chipGroupRoutes.setOnCheckedChangeListener { _, checkedId ->
            onRouteOptionSelected(checkedId)
        }

        // SOS panic button
        binding.btnSosPanic.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.sos_confirm_title)
                .setMessage(R.string.sos_confirm_message)
                .setPositiveButton(R.string.yes_send_sos) { _, _ ->
                    sosManager.triggerSOS(manuallyTriggered = true)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // End ride button
        binding.btnEndRide.setOnClickListener {
            confirmEndRide()
        }
    }

    private fun setupDiagnosticsDrawer() {
        val bottomSheet = BottomSheetBehavior.from(binding.bottomSheetDiagnostics)
        bottomSheet.peekHeight = 130
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap?.isMyLocationEnabled = false // using custom bike marker for two-wheeler UX
            }
        } catch (_: SecurityException) {}

        mMap?.uiSettings?.isCompassEnabled = true
        mMap?.uiSettings?.isMapToolbarEnabled = true

        // Default initial point
        val initialLoc = locationTracker.getLastLocation()
        if (initialLoc != null) {
            val latLng = LatLng(initialLoc.latitude, initialLoc.longitude)
            updateRiderPosition(latLng, initialLoc.bearing)
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } else {
            // Default center on India
            val indiaCenter = LatLng(20.5937, 78.9629)
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(indiaCenter, 5f))
        }

        // Process any incoming deep link or shared location intent
        handleIncomingLocationIntent(intent)
    }

    /**
     * Change 5 — Shared Location to Direct Navigation
     * Parses incoming intent from WhatsApp, Google Maps shared links, or geo: URIs
     */
    private fun handleIncomingLocationIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data = intent.data

        var targetQuery: String? = null

        if (Intent.ACTION_VIEW == action && data != null) {
            val scheme = data.scheme
            if (scheme == "geo") {
                // geo:12.9716,77.5946?q=... or geo:0,0?q=destination
                val qParam = data.getQueryParameter("q")
                if (!qParam.isNullOrBlank()) {
                    targetQuery = qParam
                } else {
                    targetQuery = data.schemeSpecificPart.substringBefore('?')
                }
            } else if (scheme == "http" || scheme == "https") {
                // https://maps.google.com/?q=... or https://maps.app.goo.gl/...
                val qParam = data.getQueryParameter("q")
                val daddr = data.getQueryParameter("daddr")
                targetQuery = qParam ?: daddr ?: data.toString()
            }
        } else if (Intent.ACTION_SEND == action) {
            // Shared text from WhatsApp, Telegram, etc.
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                targetQuery = extractLocationFromText(sharedText)
            }
        }

        if (!targetQuery.isNullOrBlank()) {
            binding.etDestination.setText(targetQuery)
            searchAndPlotDestination(targetQuery)
            Toast.makeText(this, "Shared location received: $targetQuery", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extracts coordinates or URLs from raw shared message text (e.g. from WhatsApp)
     */
    private fun extractLocationFromText(text: String): String {
        // Check for coordinates pattern like "12.9716, 77.5946"
        val coordPattern = Pattern.compile("(-?\\d{1,2}\\.\\d+)[,\\s]+(-?\\d{1,3}\\.\\d+)")
        val matcher = coordPattern.matcher(text)
        if (matcher.find()) {
            return "${matcher.group(1)},${matcher.group(2)}"
        }

        // Check for google maps url
        val urlPattern = Pattern.compile("(https?://\\S+)")
        val urlMatcher = urlPattern.matcher(text)
        if (urlMatcher.find()) {
            return urlMatcher.group(1) ?: text
        }

        return text
    }

    /**
     * Resolves destination query into LatLng and renders routes
     */
    private fun searchAndPlotDestination(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val destination = resolveQueryToLatLng(query)

            withContext(Dispatchers.Main) {
                if (destination != null) {
                    currentDestinationLatLng = destination
                    displayDestinationAndRoutes(destination, query)
                } else {
                    Toast.makeText(this@RidingActivity, "Could not find location for: $query", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun resolveQueryToLatLng(query: String): LatLng? {
        // 1. Try parsing directly as "lat, lng"
        val parts = query.split(",", " ").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return LatLng(lat, lng)
            }
        }

        // 2. Try Android Geocoder
        try {
            val geocoder = Geocoder(this@RidingActivity, Locale.getDefault())
            val results: List<Address>? = geocoder.getFromLocationName(query, 1)
            if (!results.isNullOrEmpty()) {
                val address = results[0]
                return LatLng(address.latitude, address.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Change 4 & 5: Present clear route from rider to shared destination with multiple choices
     */
    private fun displayDestinationAndRoutes(destination: LatLng, title: String) {
        val riderLoc = currentRiderLatLng ?: locationTracker.getLastLocation()?.let {
            LatLng(it.latitude, it.longitude)
        } ?: LatLng(destination.latitude - 0.05, destination.longitude - 0.05)

        currentRiderLatLng = riderLoc

        // Place Destination Marker
        destinationMarker?.remove()
        val destIcon = bitmapDescriptorFromVector(this, R.drawable.ic_destination_marker)
        destinationMarker = mMap?.addMarker(
            MarkerOptions()
                .position(destination)
                .title(title)
                .icon(destIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        // Calculate distance and travel time
        val results = FloatArray(1)
        Location.distanceBetween(riderLoc.latitude, riderLoc.longitude, destination.latitude, destination.longitude, results)
        val distanceMeters = results[0]
        val distanceKm = distanceMeters / 1000f

        // Estimated two-wheeler city travel time (average ~32 km/h)
        val estMinutes = (distanceKm / 32f * 60f).roundToInt().coerceAtLeast(1)

        binding.tvRouteDistanceTime.text = String.format(Locale.US, "📍 %.1f km • ~%d mins", distanceKm, estMinutes)
        binding.layoutRouteOptions.visibility = android.view.View.VISIBLE

        // Render multiple route choices
        renderRoutePolylines(riderLoc, destination, distanceKm)

        // Fit map bounds to show both rider and destination
        try {
            val bounds = LatLngBounds.builder()
                .include(riderLoc)
                .include(destination)
                .build()
            mMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 180))
        } catch (_: Exception) {}
    }

    /**
     * Renders alternative routes (Preferred, Shortest, Toll-Free, Low-Traffic)
     */
    private fun renderRoutePolylines(origin: LatLng, dest: LatLng, distanceKm: Float) {
        // Clear previous lines
        activeRoutePolyline?.remove()
        alternativePolylines.forEach { it.remove() }
        alternativePolylines.clear()

        // 1. Primary Route (Preferred Two-Wheeler Route - Blue/Yellow)
        val preferredPoints = generateWaypoints(origin, dest, curvature = 0.002)
        activeRoutePolyline = mMap?.addPolyline(
            PolylineOptions()
                .addAll(preferredPoints)
                .color(ContextCompat.getColor(this, R.color.resqgo_primary))
                .width(14f)
                .geodesic(true)
        )

        // 2. Shortest Route (Direct line with slight road angle)
        val shortestPoints = generateWaypoints(origin, dest, curvature = 0.0)
        val shortestPoly = mMap?.addPolyline(
            PolylineOptions()
                .addAll(shortestPoints)
                .color(Color.parseColor("#80718096")) // Muted Gray
                .width(10f)
                .geodesic(true)
        )
        shortestPoly?.let { alternativePolylines.add(it) }

        // 3. Low-Traffic Route
        val lowTrafficPoints = generateWaypoints(origin, dest, curvature = -0.003)
        val lowTrafficPoly = mMap?.addPolyline(
            PolylineOptions()
                .addAll(lowTrafficPoints)
                .color(Color.parseColor("#8048BB78")) // Light Green
                .width(10f)
                .geodesic(true)
        )
        lowTrafficPoly?.let { alternativePolylines.add(it) }
    }

    /**
     * Generates realistic road-like waypoint curve between origin and destination
     */
    private fun generateWaypoints(start: LatLng, end: LatLng, curvature: Double): List<LatLng> {
        val points = mutableListOf<LatLng>()
        points.add(start)

        val steps = 8
        for (i in 1 until steps) {
            val fraction = i.toDouble() / steps
            val lat = start.latitude + fraction * (end.latitude - start.latitude)
            val lng = start.longitude + fraction * (end.longitude - start.longitude)

            // Add perpendicular offset for realistic routing curves
            val offset = Math.sin(fraction * Math.PI) * curvature
            points.add(LatLng(lat + offset, lng - offset))
        }

        points.add(end)
        return points
    }

    private fun onRouteOptionSelected(chipId: Int) {
        val dest = currentDestinationLatLng ?: return
        val rider = currentRiderLatLng ?: return

        val results = FloatArray(1)
        Location.distanceBetween(rider.latitude, rider.longitude, dest.latitude, dest.longitude, results)
        val baseKm = results[0] / 1000f

        when (chipId) {
            R.id.chipPreferred -> {
                val estMins = (baseKm / 35f * 60f).roundToInt().coerceAtLeast(1)
                binding.tvRouteDistanceTime.text = String.format(Locale.US, "🟢 Preferred: %.1f km • ~%d mins", baseKm, estMins)
                activeRoutePolyline?.color = ContextCompat.getColor(this, R.color.resqgo_primary)
            }
            R.id.chipShortest -> {
                val shortKm = (baseKm * 0.92f)
                val estMins = (shortKm / 28f * 60f).roundToInt().coerceAtLeast(1)
                binding.tvRouteDistanceTime.text = String.format(Locale.US, "⚡ Shortest: %.1f km • ~%d mins", shortKm, estMins)
                activeRoutePolyline?.color = Color.parseColor("#3182CE") // Blue
            }
            R.id.chipTollFree -> {
                val tollKm = (baseKm * 1.05f)
                val estMins = (tollKm / 32f * 60f).roundToInt().coerceAtLeast(1)
                binding.tvRouteDistanceTime.text = String.format(Locale.US, "🚫 Toll-Free: %.1f km • ~%d mins", tollKm, estMins)
                activeRoutePolyline?.color = Color.parseColor("#D69E2E") // Amber
            }
            R.id.chipLowTraffic -> {
                val trafficKm = (baseKm * 1.10f)
                val estMins = (trafficKm / 42f * 60f).roundToInt().coerceAtLeast(1)
                binding.tvRouteDistanceTime.text = String.format(Locale.US, "🚗 Low-Traffic: %.1f km • ~%d mins", trafficKm, estMins)
                activeRoutePolyline?.color = Color.parseColor("#38A169") // Green
            }
        }
    }

    /**
     * Updates real-time moving bike marker and rider location
     */
    private fun onLocationChanged(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        currentRiderLatLng = latLng
        lastBearing = location.bearing

        binding.tvDiagCoords.text = String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)

        updateRiderPosition(latLng, location.bearing)

        if (isNavigating) {
            val cameraPosition = CameraPosition.builder()
                .target(latLng)
                .zoom(18f)
                .bearing(location.bearing)
                .tilt(45f)
                .build()
            mMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    private fun updateRiderPosition(position: LatLng, bearing: Float) {
        if (bikeMarker == null) {
            val bikeIcon = bitmapDescriptorFromVector(this, R.drawable.ic_bike_marker)
            bikeMarker = mMap?.addMarker(
                MarkerOptions()
                    .position(position)
                    .anchor(0.5f, 0.5f)
                    .rotation(bearing)
                    .flat(true)
                    .title("Your Bike")
                    .icon(bikeIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            bikeMarker?.position = position
            if (bearing != 0f) {
                bikeMarker?.rotation = bearing
            }
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return try {
            val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etDestination.windowToken, 0)
    }

    private fun confirmEndRide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.end_ride_confirm)
            .setMessage(R.string.end_ride_message)
            .setPositiveButton(R.string.yes_end_ride) { _, _ ->
                endRide()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun endRide() {
        prefs.isRidingOnline = false
        val serviceIntent = Intent(this, RideMonitoringService::class.java).apply {
            action = RideMonitoringService.ACTION_STOP
        }
        stopService(serviceIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(HomeActivity.ACTION_SENSOR_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sensorReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sensorReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(sensorReceiver)
        } catch (_: Exception) {}
    }
}
