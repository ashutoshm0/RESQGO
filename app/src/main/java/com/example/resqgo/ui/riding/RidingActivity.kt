package com.example.resqgo.ui.riding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivityRidingBinding
import com.example.resqgo.location.LocationTracker
import com.example.resqgo.navigation.RouteFlavour
import com.example.resqgo.navigation.RoutePreferences
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import com.example.resqgo.ui.contacts.EmergencyContactsActivity
import com.example.resqgo.ui.home.HomeActivity
import com.example.resqgo.ui.settings.SettingsActivity
import com.example.resqgo.util.PermissionHelper
import com.example.resqgo.util.applySystemBarPadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

private const val TAG = "RidingActivity"

/**
 * Navigation + active-ride screen.
 *
 * Rendered with osmdroid (OpenStreetMap) rather than the Google Maps SDK: it
 * needs no API key and no billing account, and it caches tiles to disk, which
 * suits an app built for patchy-network riding.
 */
class RidingActivity : AppCompatActivity(), TripOptionsSheet.Listener {

    companion object {
        /** Set false for a safety-only ride: monitoring runs, no destination asked for. */
        const val EXTRA_ASK_DESTINATION = "ask_destination"
    }

    private lateinit var binding: ActivityRidingBinding
    private lateinit var prefs: UserPreferences
    private lateinit var sosManager: SOSManager
    private lateinit var locationTracker: LocationTracker
    private var diagnosticsSheet: BottomSheetBehavior<*>? = null

    private var bikeMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private val stopMarkers = mutableListOf<Marker>()
    private var activeRoutePolyline: Polyline? = null
    private val alternativePolylines = mutableListOf<Polyline>()
    private var currentRouteResult: com.example.resqgo.navigation.OSRMClient.RouteResult? = null

    private var currentRiderLatLng: GeoPoint? = null
    private var currentDestinationLatLng: GeoPoint? = null
    private var destinationLabel: String = ""
    private var isNavigating = false
    private var lastBearing = 0f
    private var topoView = false

    private var selectedRoute = RouteFlavour.RECOMMENDED
    private var routePrefs = RoutePreferences()

    /** Intermediate stops added via Trip Options, in visiting order. */
    private val stops = mutableListOf<Pair<String, GeoPoint>>()

    private val routeCards: Map<RouteFlavour, Pair<MaterialCardView, TextView>> by lazy {
        mapOf(
            RouteFlavour.SHORTEST to (binding.cardRouteShortest to binding.tvTimeShortest),
            RouteFlavour.TOLL_FREE to (binding.cardRouteTollFree to binding.tvTimeTollFree),
            RouteFlavour.LOW_TRAFFIC to (binding.cardRouteLowTraffic to binding.tvTimeLowTraffic),
            RouteFlavour.RECOMMENDED to (binding.cardRouteBetter to binding.tvTimeBetter)
        )
    }

    // Sensor broadcast receiver from background RideMonitoringService
    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HomeActivity.ACTION_SENSOR_UPDATE) return

            val gForce = intent.getFloatExtra("gForce", 9.8f)
            val speedKmh = intent.getFloatExtra("speedKmh", -1f)
            val gx = intent.getFloatExtra("gx", 0f)
            val gy = intent.getFloatExtra("gy", 0f)
            val gpsLocked = intent.getBooleanExtra("gpsLocked", false)

            binding.tvDiagGForce.text = String.format(Locale.US, "%.2f G", gForce / 9.80665f)
            binding.tvDiagGyroX.text = String.format(Locale.US, "%.2f rad/s", gx)
            binding.tvDiagGyroY.text = String.format(Locale.US, "%.2f rad/s", gy)
            binding.tvDiagGps.text = if (gpsLocked) "Locked (3D)" else "Searching..."
            binding.tvDiagGps.setTextColor(
                ContextCompat.getColor(
                    this@RidingActivity,
                    if (gpsLocked) R.color.resqgo_success else R.color.resqgo_alert
                )
            )

            if (speedKmh >= 0) {
                binding.tvSpeed.text = String.format(Locale.US, "%d km/h", speedKmh.roundToInt())
            }

            locationTracker.getLastLocation()?.let { onLocationChanged(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRidingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topBarContainer.applySystemBarPadding(top = true)
        binding.bottomContainer.applySystemBarPadding(bottom = true)

        prefs = UserPreferences(this)
        routePrefs = prefs.routePreferences
        sosManager = SOSManager(this)
        locationTracker = LocationTracker(this)

        onBackPressedDispatcher.addCallback(this) { confirmEndRide() }

        setupMap()
        setupUI()
        if (intent.getBooleanExtra(EXTRA_ASK_DESTINATION, false)) {
            binding.etDestination.requestFocus()
        }
        setupDiagnosticsDrawer()
        setupBottomNav()

        if (!prefs.isRidingOnline) startMonitoringService()

        handleIncomingLocationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLocationIntent(intent)
    }

    // ── Map ──────────────────────────────────────────────────────────────────

    /**
     * Esri's World Street Map: free, needs no API key and — unlike
     * OpenStreetMap's volunteer servers — permits ordinary app traffic.
     * OSM's own tiles return 403 "Access blocked" for apps like this one.
     * Esri serves tiles as /{z}/{y}/{x} with no extension, hence the override.
     */
    private val esriStreets = object : OnlineTileSourceBase(
        "EsriWorldStreetMap", 0, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
        "Esri, HERE, Garmin, © OpenStreetMap contributors"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) +
                "/" + MapTileIndex.getY(pMapTileIndex) +
                "/" + MapTileIndex.getX(pMapTileIndex)
    }

    /** Satellite imagery for the layers toggle — also key-free. */
    private val esriImagery = object : OnlineTileSourceBase(
        "EsriWorldImagery", 0, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "Esri, Maxar, Earthstar Geographics"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) +
                "/" + MapTileIndex.getY(pMapTileIndex) +
                "/" + MapTileIndex.getX(pMapTileIndex)
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(esriStreets)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
        binding.tvMapUnavailable.visibility = View.GONE

        val initial = locationTracker.getLastLocation()
        val start = if (initial != null) {
            GeoPoint(initial.latitude, initial.longitude)
        } else {
            GeoPoint(20.5937, 78.9629) // India
        }
        binding.mapView.controller.setZoom(if (initial != null) 16.0 else 5.0)
        binding.mapView.controller.setCenter(start)
        if (initial != null) updateRiderPosition(start, initial.bearing)
    }

    private fun startMonitoringService() {
        prefs.isRidingOnline = true
        val serviceIntent = Intent(this, RideMonitoringService::class.java).apply {
            action = RideMonitoringService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            prefs.isRidingOnline = false
            binding.tvMonitoringStatus.text = getString(R.string.ride_start_failed_title)
            binding.tvMonitoringStatus.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_alert)
            )
        }
    }

    private fun setupUI() {
        binding.tvSosTimerDisplay.text =
            getString(R.string.auto_sos_timer_info, formatTimer(prefs.sosTimerSeconds))
        binding.tvRouteDistanceTime.text = getString(R.string.nav_no_destination)
        binding.tvRouteVia.text = getString(R.string.nav_no_destination_hint)

        binding.tvCurrentLocation.setOnClickListener {
            if (!PermissionHelper.hasLocation(this)) PermissionHelper.openAppSettings(this)
        }

        binding.etDestination.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitDestination()
                true
            } else {
                false
            }
        }
        binding.btnSearchDestination.setOnClickListener { submitDestination() }
        binding.btnTripOptions.setOnClickListener { openTripOptions() }
        binding.btnMenu.setOnClickListener { startActivity(Intent(this, HomeActivity::class.java)) }

        binding.fabRecenter.setOnClickListener {
            currentRiderLatLng?.let {
                binding.mapView.controller.animateTo(it, 17.0, 600L)
            } ?: Toast.makeText(this, R.string.nav_waiting_gps, Toast.LENGTH_SHORT).show()
        }

        // Layers button toggles street map <-> satellite imagery.
        binding.fabMapType.setOnClickListener {
            topoView = !topoView
            binding.mapView.setTileSource(
                if (topoView) esriImagery else esriStreets
            )
        }

        binding.tvMonitoringStatus.setOnClickListener {
            diagnosticsSheet?.state = BottomSheetBehavior.STATE_EXPANDED
        }

        routeCards.forEach { (option, views) ->
            views.first.setOnClickListener { selectRoute(option) }
        }
        binding.btnStartNavigation.setOnClickListener { toggleNavigation() }

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
        binding.btnEndRide.setOnClickListener { confirmEndRide() }
    }

    /** The safety banner must tell the truth about what is actually monitored. */
    private fun updateMonitoringStatus() {
        if (PermissionHelper.hasLocation(this)) {
            binding.tvMonitoringStatus.text = getString(R.string.status_monitoring)
            binding.tvMonitoringStatus.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_success)
            )
        } else {
            binding.tvMonitoringStatus.text = getString(R.string.status_sensors_only)
            binding.tvMonitoringStatus.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_alert)
            )
            binding.tvCurrentLocation.text = getString(R.string.nav_location_off)
        }
    }

    // ── Trip / Route Options (Change 10) ─────────────────────────────────────

    private fun openTripOptions() {
        val sheet = TripOptionsSheet()
        sheet.listener = this
        sheet.setInitialState(routePrefs, stopsSummary())
        sheet.show(supportFragmentManager, "trip_options")
    }

    private fun stopsSummary(): String? =
        if (stops.isEmpty()) null
        else getString(R.string.trip_stops_summary, stops.size, stops.joinToString(", ") { it.first })

    override fun onPreferencesChanged(prefs: RoutePreferences) {
        routePrefs = prefs
        this.prefs.routePreferences = prefs
        // Routes recalculate immediately, as the spec requires.
        if (currentDestinationLatLng != null) {
            refreshRouteEtas()
            selectRoute(if (routePrefs.permits(selectedRoute)) selectedRoute else RouteFlavour.SHORTEST)
            currentRiderLatLng?.let { rider ->
                currentDestinationLatLng?.let { dest -> renderRoutePolylines(rider, dest) }
            }
        }
    }

    override fun onAddStopRequested() {
        if (currentDestinationLatLng == null) {
            Toast.makeText(this, R.string.trip_need_destination, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = getString(R.string.trip_add_stop_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.trip_add_stop_title)
            .setView(input)
            .setPositiveButton(R.string.trip_add_stop) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isEmpty()) return@setPositiveButton
                CoroutineScope(Dispatchers.IO).launch {
                    val point = resolveQueryToGeoPoint(query)
                    withContext(Dispatchers.Main) {
                        if (point == null) {
                            Toast.makeText(
                                this@RidingActivity,
                                getString(R.string.nav_not_found, query),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            stops.add(query to point)
                            addPlainMarker(point, query)
                            refreshRouteEtas()
                            selectRoute(selectedRoute)
                            currentRiderLatLng?.let { r ->
                                currentDestinationLatLng?.let { d -> renderRoutePolylines(r, d) }
                            }
                            Toast.makeText(
                                this@RidingActivity,
                                R.string.trip_stop_added,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onShareRouteRequested() {
        val dest = currentDestinationLatLng
        if (dest == null) {
            Toast.makeText(this, R.string.trip_need_destination, Toast.LENGTH_SHORT).show()
            return
        }
        val nl = System.lineSeparator()
        val body = buildString {
            append("RESQGO route to ").append(destinationLabel.ifBlank { "my destination" }).append(nl)
            append("https://www.openstreetmap.org/?mlat=")
                .append(dest.latitude).append("&mlon=").append(dest.longitude).append(nl)
            append(getString(selectedRoute.labelRes)).append(" • ")
            append(getString(R.string.eta_summary, etaMinutes(selectedRoute), routeKm(selectedRoute)))
            if (stops.isNotEmpty()) {
                append(nl).append("Via: ").append(stops.joinToString(", ") { it.first })
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.trip_share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(send, getString(R.string.trip_share)))
    }

    override fun onSaveRouteRequested() {
        val dest = currentDestinationLatLng
        if (dest == null) {
            Toast.makeText(this, R.string.trip_need_destination, Toast.LENGTH_SHORT).show()
            return
        }
        val label = destinationLabel.ifBlank { "Destination" }
        prefs.saveRoute(label + "|" + dest.latitude + "|" + dest.longitude)
        Toast.makeText(this, R.string.trip_route_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onStartRequested() {
        if (!isNavigating) toggleNavigation()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.navRide
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navHome -> {
                    startActivity(Intent(this, HomeActivity::class.java)); true
                }
                R.id.navSafety -> {
                    startActivity(Intent(this, EmergencyContactsActivity::class.java)); true
                }
                R.id.navProfile -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                R.id.navRide -> true
                else -> false
            }
        }
    }

    private fun setupDiagnosticsDrawer() {
        diagnosticsSheet = BottomSheetBehavior.from(binding.bottomSheetDiagnostics).apply {
            isHideable = true
            peekHeight = 0
            state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun submitDestination() {
        hideKeyboard()
        val query = binding.etDestination.text.toString().trim()
        if (query.isNotEmpty()) {
            searchAndPlotDestination(query)
        } else {
            Toast.makeText(this, R.string.nav_search_empty, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleNavigation() {
        if (currentDestinationLatLng == null) {
            Toast.makeText(this, R.string.nav_search_empty, Toast.LENGTH_SHORT).show()
            return
        }
        isNavigating = !isNavigating
        binding.btnStartNavigation.text =
            getString(if (isNavigating) R.string.nav_navigating else R.string.nav_start_ride)
        currentRiderLatLng?.let {
            binding.mapView.controller.animateTo(it, if (isNavigating) 18.0 else 15.0, 600L)
        }
    }

    /** Change 5 — Shared Location to Direct Navigation. */
    private fun handleIncomingLocationIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data
        var targetQuery: String? = null

        try {
            if (Intent.ACTION_VIEW == action && data != null) {
                when (data.scheme) {
                    // "geo:" is an OPAQUE uri — Uri.getQueryParameter() throws on it.
                    "geo" -> {
                        val ssp = data.schemeSpecificPart.orEmpty()
                        val q = ssp.substringAfter("q=", "").substringBefore('&')
                        targetQuery = if (q.isNotBlank()) {
                            Uri.decode(q.replace("+", " "))
                        } else {
                            ssp.substringBefore('?')
                        }
                    }
                    "http", "https" -> {
                        targetQuery = if (data.isHierarchical) {
                            data.getQueryParameter("q")
                                ?: data.getQueryParameter("daddr")
                                ?: data.toString()
                        } else {
                            data.toString()
                        }
                    }
                }
            } else if (Intent.ACTION_SEND == action) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
                    targetQuery = extractLocationFromText(it)
                }
            }
        } catch (e: Exception) {
            // A malformed share must never take the safety app down.
            Log.w(TAG, "Could not parse shared location", e)
            return
        }

        targetQuery?.takeIf { it.isNotBlank() }?.let { query ->
            binding.etDestination.setText(query)
            searchAndPlotDestination(query)
        }
    }

    private fun extractLocationFromText(text: String): String {
        val coord = Pattern.compile("(-?\\d{1,2}\\.\\d+)[,\\s]+(-?\\d{1,3}\\.\\d+)").matcher(text)
        if (coord.find()) return coord.group(1) + "," + coord.group(2)
        val url = Pattern.compile("(https?://\\S+)").matcher(text)
        if (url.find()) return url.group(1) ?: text
        return text
    }

    private fun searchAndPlotDestination(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val destination = resolveQueryToGeoPoint(query)
            withContext(Dispatchers.Main) {
                if (destination != null) {
                    currentDestinationLatLng = destination
                    destinationLabel = query
                    displayDestinationAndRoutes(destination, query)
                } else {
                    Toast.makeText(
                        this@RidingActivity,
                        getString(R.string.nav_not_found, query),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Uses OSM Nominatim as a reliable alternative to Android Geocoder. */
    private suspend fun resolveQueryToGeoPoint(query: String): GeoPoint? {
        // 1. Try parsing directly as "lat, lng"
        val parts = query.split(",", " ").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return GeoPoint(lat, lng)
            }
        }
        // 2. Fall back to Nominatim
        return com.example.resqgo.navigation.NominatimClient.searchPlace(query)
    }

    private fun displayDestinationAndRoutes(destination: GeoPoint, title: String) {
        val riderLoc = currentRiderLatLng
            ?: locationTracker.getLastLocation()?.let { GeoPoint(it.latitude, it.longitude) }
            ?: GeoPoint(destination.latitude - 0.05, destination.longitude - 0.05)
        currentRiderLatLng = riderLoc

        destinationMarker?.let { binding.mapView.overlays.remove(it) }
        destinationMarker = Marker(binding.mapView).apply {
            position = destination
            this.title = title
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@RidingActivity, R.drawable.ic_destination_marker)
        }.also { binding.mapView.overlays.add(it) }

        binding.layoutRouteOptions.visibility = View.VISIBLE
        refreshRouteEtas()
        selectRoute(selectedRoute)
        renderRoutePolylines(riderLoc, destination)

        try {
            val pts = listOf(riderLoc, destination) + stops.map { it.second }
            binding.mapView.zoomToBoundingBox(
                BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.4f), true, 120
            )
        } catch (_: Exception) {
        }
        binding.mapView.invalidate()
    }

    private fun addPlainMarker(point: GeoPoint, label: String) {
        val marker = Marker(binding.mapView).apply {
            position = point
            title = label
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        stopMarkers.add(marker)
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()
    }

    // ── Route maths ──────────────────────────────────────────────────────────

    /** Total journey distance: rider -> each stop in order -> destination. */
    private fun baseDistanceKm(): Float {
        val rider = currentRiderLatLng ?: return 0f
        val dest = currentDestinationLatLng ?: return 0f
        val legs = buildList {
            add(rider)
            stops.forEach { add(it.second) }
            add(dest)
        }
        var metres = 0f
        for (i in 1 until legs.size) {
            val r = FloatArray(1)
            Location.distanceBetween(
                legs[i - 1].latitude, legs[i - 1].longitude,
                legs[i].latitude, legs[i].longitude, r
            )
            metres += r[0]
        }
        return metres / 1000f
    }

    private fun routeKm(flavour: RouteFlavour): Float {
        val base = currentRouteResult?.let { (it.distanceMetres / 1000.0).toFloat() } ?: baseDistanceKm()
        return base * flavour.distanceFactor * routePrefs.distancePenalty()
    }

    private fun etaMinutes(flavour: RouteFlavour): Int {
        val baseMinutes = currentRouteResult?.let { (it.durationSeconds / 60.0).toFloat() } 
        
        if (baseMinutes != null) {
            val adjusted = baseMinutes * flavour.speedFactor * routePrefs.speedPenalty()
            return adjusted.roundToInt().coerceAtLeast(1)
        }
        
        // Fallback to straight-line maths
        val speed = routePrefs.travelMode.avgSpeedKmh * flavour.speedFactor * routePrefs.speedPenalty()
        if (speed <= 0f) return 1
        return (routeKm(flavour) / speed * 60f).roundToInt().coerceAtLeast(1)
    }

    /** Recomputes every route card; options the preferences rule out are dimmed. */
    private fun refreshRouteEtas() {
        routeCards.forEach { (flavour, views) ->
            val (card, eta) = views
            val allowed = routePrefs.permits(flavour)
            card.isEnabled = allowed
            card.alpha = if (allowed) 1f else 0.4f
            eta.text = if (allowed) getString(R.string.eta_minutes, etaMinutes(flavour)) else "—"
        }
        if (!routePrefs.permits(selectedRoute)) {
            RouteFlavour.entries.firstOrNull { routePrefs.permits(it) }?.let { selectRoute(it) }
        }
    }

    private fun selectRoute(option: RouteFlavour) {
        selectedRoute = option
        val onPrimary = ContextCompat.getColor(this, R.color.white)
        routeCards.forEach { (candidate, views) ->
            val (card, etaView) = views
            val checked = candidate == option
            card.isChecked = checked
            val title = (card.getChildAt(0) as? android.view.ViewGroup)?.getChildAt(0) as? TextView
            if (checked) {
                title?.setTextColor(onPrimary)
                etaView.setTextColor(onPrimary)
            } else {
                title?.setTextColor(themeColor(com.google.android.material.R.attr.colorOnBackground))
                etaView.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }
        binding.tvRouteDistanceTime.text =
            getString(R.string.eta_summary, etaMinutes(option), routeKm(option))
        binding.tvRouteVia.text = getString(
            R.string.eta_via,
            getString(option.labelRes),
            destinationLabel.ifBlank { getString(R.string.nav_your_location) }
        )
        activeRoutePolyline?.outlinePaint?.color = Color.parseColor(option.colorHex)
        binding.mapView.invalidate()
    }

    private fun themeColor(attr: Int): Int {
        val typed = android.util.TypedValue()
        theme.resolveAttribute(attr, typed, true)
        return typed.data
    }

    private fun renderRoutePolylines(origin: GeoPoint, dest: GeoPoint) {
        activeRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        alternativePolylines.forEach { binding.mapView.overlays.remove(it) }
        alternativePolylines.clear()

        val via = buildList {
            add(origin)
            stops.forEach { add(it.second) }
            add(dest)
        }

        CoroutineScope(Dispatchers.Main).launch {
            val routeResult = com.example.resqgo.navigation.OSRMClient.getRoute(via)
            if (routeResult != null) {
                currentRouteResult = routeResult
                refreshRouteEtas()
                selectRoute(selectedRoute)
                
                // Remove again in case user rapidly changed destination
                activeRoutePolyline?.let { binding.mapView.overlays.remove(it) }
                
                activeRoutePolyline = Polyline().apply {
                    setPoints(routeResult.points)
                    outlinePaint.color = Color.parseColor(selectedRoute.colorHex)
                    outlinePaint.strokeWidth = 14f
                }.also { binding.mapView.overlays.add(it) }
                
                binding.mapView.invalidate()
            } else {
                Toast.makeText(this@RidingActivity, "Routing service unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Live position ────────────────────────────────────────────────────────

    private fun onLocationChanged(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        currentRiderLatLng = point
        lastBearing = location.bearing

        binding.tvDiagCoords.text =
            String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)

        updateRiderPosition(point, location.bearing)
        if (currentDestinationLatLng != null) refreshRouteEtas()
        if (isNavigating) binding.mapView.controller.animateTo(point, 18.0, 800L)
    }

    private fun updateRiderPosition(position: GeoPoint, bearing: Float) {
        if (bikeMarker == null) {
            bikeMarker = Marker(binding.mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Your Bike"
                icon = ContextCompat.getDrawable(this@RidingActivity, R.drawable.ic_bike_marker)
                isFlat = true
            }.also { binding.mapView.overlays.add(it) }
        }
        bikeMarker?.apply {
            this.position = position
            // osmdroid rotates anticlockwise; GPS bearing is clockwise from north.
            if (bearing != 0f) rotation = -bearing
        }
        binding.mapView.invalidate()
        updateCurrentLocationLabel(position)
    }

    private var lastGeocodedAt = 0L

    /** Reverse-geocodes the rider position at most once every 20 s. */
    private fun updateCurrentLocationLabel(position: GeoPoint) {
        val now = System.currentTimeMillis()
        if (now - lastGeocodedAt < 20_000L) return
        lastGeocodedAt = now
        CoroutineScope(Dispatchers.IO).launch {
            val label = locationTracker.getAddressFromLocation(position.latitude, position.longitude)
            withContext(Dispatchers.Main) {
                if (label.isNotBlank() && PermissionHelper.hasLocation(this@RidingActivity)) {
                    binding.tvCurrentLocation.text = label
                }
            }
        }
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    private fun formatTimer(seconds: Int): String =
        if (seconds < 60) {
            seconds.toString() + "s"
        } else {
            val mins = (seconds / 60).toString() + " min"
            if (seconds % 60 != 0) mins + " " + (seconds % 60) + "s" else mins
        }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etDestination.windowToken, 0)
    }

    private fun confirmEndRide() {
        AlertDialog.Builder(this)
            .setTitle(R.string.end_ride_confirm)
            .setMessage(R.string.end_ride_message)
            .setPositiveButton(R.string.yes_end_ride) { _, _ -> endRide() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun endRide() {
        prefs.isRidingOnline = false
        val serviceIntent = Intent(this, RideMonitoringService::class.java).apply {
            action = RideMonitoringService.ACTION_STOP
        }
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            stopService(serviceIntent)
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        binding.tvSosTimerDisplay.text =
            getString(R.string.auto_sos_timer_info, formatTimer(prefs.sosTimerSeconds))
        updateMonitoringStatus()
        val filter = IntentFilter(HomeActivity.ACTION_SENSOR_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sensorReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sensorReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        try {
            unregisterReceiver(sensorReceiver)
        } catch (_: Exception) {
        }
    }
}
