package com.example.resqgo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.data.repository.RideRepository
import com.example.resqgo.detection.DetectionResult
import com.example.resqgo.detection.DetectionThresholds
import com.example.resqgo.detection.RuleBasedDetector
import com.example.resqgo.location.LocationTracker
import com.example.resqgo.sensors.AccelerometerData
import com.example.resqgo.sensors.GyroscopeData
import com.example.resqgo.ui.confirmation.ConfirmationActivity
import com.example.resqgo.ui.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RideMonitoringService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private lateinit var notificationManager: NotificationManager

    private val prefs by lazy { UserPreferences(this) }
    private val detector by lazy {
        RuleBasedDetector(DetectionThresholds.getThreshold(prefs.sensitivity))
    }
    val locationTracker by lazy { LocationTracker(this) }

    private val repository: RideRepository
        get() = (application as com.example.resqgo.ResqgoApp).repository

    private var rideStartTime: Long = 0L
    // Last gyroscope readings (updated each gyro event, broadcast with accel)
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f

    companion object {
        const val ACTION_START = "START_MONITORING"
        const val ACTION_STOP  = "STOP_MONITORING"

        // Service notification (persistent, low priority)
        private const val RIDE_NOTIFICATION_ID = 1
        private const val RIDE_CHANNEL_ID      = "ride_monitoring"

        // Crash alert notification (high priority, full-screen)
        private const val CRASH_NOTIFICATION_ID = 911
        private const val CRASH_CHANNEL_ID      = "crash_alert"

        @Volatile var isConfirmationShowing = false
            private set

        fun resetConfirmationFlag() {
            isConfirmationShowing = false
        }

        // Singleton reference so SOSManager can get location instantly
        @Volatile var instance: RideMonitoringService? = null
            private set
    }

    // ────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        instance = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP  -> stopMonitoring()
            // START_STICKY hands us a null intent after the system restarts the
            // process. We must call startForeground() again immediately or Android
            // kills the app with RemoteServiceException.
            null -> if (prefs.isRidingOnline) startMonitoring() else stopSelf()
        }
        return START_STICKY
    }

    // ────────────────────────────────────────────────────────
    private fun startMonitoring() {
        val notification = buildRideNotification()

        // Android 14+ throws SecurityException if we claim the `location` foreground
        // service type without holding a location runtime permission — that killed the
        // app on first launch. Fall back to `dataSync` so sensor-only crash detection
        // still runs, and never let this path take the process down.
        if (!startForegroundSafely(notification)) {
            Log.e("RideService", "Foreground service refused by system — stopping")
            prefs.isRidingOnline = false
            stopSelf()
            return
        }

        rideStartTime = System.currentTimeMillis()
        if (hasLocationPermission()) {
            locationTracker.startTracking()   // ← GPS breadcrumb recording starts
        } else {
            Log.w("RideService", "No location permission — GPS breadcrumbs disabled")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loc = locationTracker.getLastLocation()
                repository.logEvent(
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                    eventType = "RIDE_START"
                )
            } catch (e: Exception) {
                Log.e("RideService", "Failed to log ride start", e)
            }
        }

        try {
            // SENSOR_DELAY_GAME = 20 ms (50 Hz). A motorcycle impact impulse lasts
            // 100-200 ms; at SENSOR_DELAY_NORMAL (200 ms) the peak falls between
            // samples and the crash is never seen. Bench: 0/40 caught at 5 Hz,
            // 40/40 at 50 Hz. See CrashDetectorBenchTest.
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                ?: Log.w("RideService", "Accelerometer unavailable")
            gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                ?: Log.w("RideService", "Gyroscope unavailable")
        } catch (e: Exception) {
            Log.e("RideService", "Sensor registration failed", e)
        }
    }

    /**
     * Promotes the service to the foreground with a service type the app is
     * actually allowed to use. Returns false if the platform rejected every option.
     */
    private fun startForegroundSafely(notification: Notification): Boolean {
        val types = if (hasLocationPermission()) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(RIDE_NOTIFICATION_ID, notification, types)
            } else {
                startForeground(RIDE_NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e("RideService", "startForeground($types) failed", e)
            // Last resort: no explicit type at all.
            try {
                startForeground(RIDE_NOTIFICATION_ID, notification)
                true
            } catch (e2: Exception) {
                Log.e("RideService", "startForeground fallback failed", e2)
                false
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        com.example.resqgo.util.PermissionHelper.hasLocation(this)

    private fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        locationTracker.stopTracking()    // ← GPS stops, breadcrumbs saved
        detector.reset()
        isConfirmationShowing = false
        instance = null
        notificationManager.cancel(CRASH_NOTIFICATION_ID)
        
        val duration = System.currentTimeMillis() - rideStartTime
        val distance = locationTracker.getTotalDistanceKm()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loc = locationTracker.getLastLocation()
                repository.logEvent(
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                    eventType = "RIDE_END",
                    durationMs = duration,
                    distanceKm = distance
                )
            } catch (e: Exception) {
                Log.e("RideService", "Failed to log ride end", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    // ────────────────────────────────────────────────────────
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isConfirmationShowing) return

        val timestamp = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val accel = AccelerometerData(event.values[0], event.values[1], event.values[2], timestamp)
                broadcastSensorUpdate(
                    gForce = accel.magnitude(),
                    ax = accel.x, ay = accel.y, az = accel.z,
                    gx = lastGyroX,  gy = lastGyroY,  gz = lastGyroZ
                )
                val gyro = com.example.resqgo.sensors.GyroscopeData(lastGyroX, lastGyroY, lastGyroZ, timestamp)
                val speedMps = locationTracker.getLastLocation()?.speed
                val result = detector.analyze(accel, gyro, speedMps)
                if (result is DetectionResult.PossibleAccident) {
                    Log.d("RideService", "Crash detected: ${result.reason}")
                    triggerCrashAlert()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0]
                lastGyroY = event.values[1]
                lastGyroZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    // ────────────────────────────────────────────────────────
    private fun triggerCrashAlert() {
        if (isConfirmationShowing) return
        isConfirmationShowing = true
        detector.reset()

        // 1. Fire high-priority crash notification (visible even when phone is locked/minimized)
        showCrashNotification()

        // 2. Launch full-screen ConfirmationActivity
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /**
     * Shows a HEADS-UP / full-screen notification visible even when the app is minimized
     * or the phone is locked. Includes "I'M OK" action button.
     */
    private fun showCrashNotification() {
        // PendingIntent → ConfirmationActivity (tap notification)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ConfirmationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "I'M OK" action → CancelCrashReceiver
        val imOkIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, CancelCrashReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timerDuration = prefs.sosTimerSeconds
        val notification = NotificationCompat.Builder(this, CRASH_CHANNEL_ID)
            .setContentTitle("🚨 Possible Accident Detected!")
            .setContentText("Tap I'M OK to cancel. Auto-calling emergency contacts in $timerDuration seconds.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)   // Show on lock screen
            .setFullScreenIntent(openIntent, true)                 // Wake screen
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'M OK", imOkIntent)
            .setContentIntent(openIntent)
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT  // repeat sound

        notificationManager.notify(CRASH_NOTIFICATION_ID, notification)
    }

    // ────────────────────────────────────────────────────────
    private fun broadcastSensorUpdate(
        gForce: Float,
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float
    ) {
        val lastLoc    = locationTracker.getLastLocation()
        val speedKmh   = (lastLoc?.speed ?: -1f) * 3.6f
        val gpsLocked  = lastLoc != null && lastLoc.accuracy < 20f

        val intent = Intent(HomeActivity.ACTION_SENSOR_UPDATE).apply {
            setPackage(packageName)
            putExtra("gForce",    gForce)
            putExtra("duration",  System.currentTimeMillis() - rideStartTime)
            putExtra("ax", ax); putExtra("ay", ay); putExtra("az", az)
            putExtra("gx", gx); putExtra("gy", gy); putExtra("gz", gz)
            putExtra("speedKmh",  speedKmh)
            putExtra("gpsLocked", gpsLocked)
        }
        sendBroadcast(intent)
    }

    // ────────────────────────────────────────────────────────
    private fun buildRideNotification(): Notification {
        val rideIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.example.resqgo.ui.riding.RidingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RIDE_CHANNEL_ID)
            .setContentTitle("🛡️ RESQGO Safety Active")
            .setContentText("Monitoring two-wheeler in background while you ride")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(rideIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Low-priority ride monitoring channel
            val rideChannel = NotificationChannel(
                RIDE_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )

            // HIGH-priority crash alert channel
            val crashChannel = NotificationChannel(
                CRASH_CHANNEL_ID,
                "Crash Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen alert when a possible accident is detected"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(rideChannel)
            notificationManager.createNotificationChannel(crashChannel)
        }
    }
}
