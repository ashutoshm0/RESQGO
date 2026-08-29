package com.example.resqgo.ui.riding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivityRidingBinding
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import com.example.resqgo.ui.home.HomeActivity
import kotlin.math.abs

class RidingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRidingBinding
    private lateinit var prefs: UserPreferences
    private lateinit var sosManager: SOSManager
    private val handler = Handler(Looper.getMainLooper())
    private var isDotVisible = true

    // Blink the monitoring dot
    private val dotRunnable = object : Runnable {
        override fun run() {
            isDotVisible = !isDotVisible
            binding.tvMonitoringDot.visibility =
                if (isDotVisible) View.VISIBLE else View.INVISIBLE
            handler.postDelayed(this, 800)
        }
    }

    private val sensorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != HomeActivity.ACTION_SENSOR_UPDATE) return

            val gForce   = intent.getFloatExtra("gForce",  9.8f)
            val duration = intent.getLongExtra("duration",  0L)
            val ax = intent.getFloatExtra("ax", 0f)
            val ay = intent.getFloatExtra("ay", 0f)
            val az = intent.getFloatExtra("az", 9.8f)
            val gx = intent.getFloatExtra("gx", 0f)
            val gy = intent.getFloatExtra("gy", 0f)
            val gz = intent.getFloatExtra("gz", 0f)
            val speedKmh = intent.getFloatExtra("speedKmh", -1f)
            val gpsLocked = intent.getBooleanExtra("gpsLocked", false)

            updateUI(gForce, duration, ax, ay, az, gx, gy, gz, speedKmh, gpsLocked)
        }
    }

    // ────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityRidingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs     = UserPreferences(this)
        sosManager = SOSManager(this)
        setupListeners()
        onBackPressedDispatcher.addCallback(this) {
            promptEndRide()
        }
        handler.post(dotRunnable)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(HomeActivity.ACTION_SENSOR_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sensorReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(sensorReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(sensorReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(dotRunnable)
    }

    // ────────────────────────────────────────────────────────
    private fun setupListeners() {
        binding.btnEndRide.setOnClickListener { promptEndRide() }

        binding.btnSosPanic.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Send SOS?")
                .setMessage("This will immediately alert all your emergency contacts.")
                .setPositiveButton("Yes, Send SOS") { _, _ ->
                    sosManager.triggerSOS(manuallyTriggered = true)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun promptEndRide() {
        AlertDialog.Builder(this)
            .setTitle("End Ride?")
            .setMessage("Crash monitoring will stop. Are you sure?")
            .setPositiveButton("Yes, End Ride") { _, _ -> endRide() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun endRide() {
        val intent = Intent(this, RideMonitoringService::class.java).apply {
            action = RideMonitoringService.ACTION_STOP
        }
        startService(intent)
        prefs.isRidingOnline = false
        finish()
    }

    // ────────────────────────────────────────────────────────
    private fun updateUI(
        gForce: Float, durationMs: Long,
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        speedKmh: Float = -1f, gpsLocked: Boolean = false
    ) {
        updateDuration(durationMs)
        updateGForce(gForce)
        updateAccelerometer(ax, ay, az)
        updateGyroscope(gx, gy, gz)
        updateGpsStrip(speedKmh, gpsLocked)
    }

    private fun updateDuration(durationMs: Long) {
        val h  = durationMs / 3_600_000
        val m  = (durationMs / 60_000) % 60
        val s  = (durationMs / 1_000) % 60
        binding.tvRideDuration.text = String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun updateGForce(gForce: Float) {
        // G-force bar: map 0–50 m/s² → 0–100%
        val progress = ((gForce / 50f) * 100).toInt().coerceIn(0, 100)
        binding.progressGForce.progress = progress
        binding.tvGForceValue.text = String.format("%.2f m/s²  (%.2fG)", gForce, gForce / 9.8f)

        val (colorRes, statusText) = when {
            gForce < 20f -> Pair(R.color.resqgo_success, "Normal")
            gForce < 28f -> Pair(R.color.resqgo_warning, "⚠ Warning")
            else         -> Pair(R.color.resqgo_alert,   "🚨 High Impact!")
        }
        binding.progressGForce.progressTintList =
            ContextCompat.getColorStateList(this, colorRes)
        binding.tvGForceValue.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvImpactStatus.text = statusText
        binding.tvImpactStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    /** Accelerometer: range ±20 m/s², centred at 100 in 0-200 scale */
    private fun updateAccelerometer(ax: Float, ay: Float, az: Float) {
        binding.tvAccelX.text = String.format("%+.2f", ax)
        binding.tvAccelY.text = String.format("%+.2f", ay)
        binding.tvAccelZ.text = String.format("%+.2f", az)

        binding.progressAccelX.progress = axisToProgress(ax)
        binding.progressAccelY.progress = axisToProgress(ay)
        binding.progressAccelZ.progress = axisToProgress(az)
    }

    /** Gyroscope: range ±10 rad/s, centred at 100 in 0-200 scale */
    private fun updateGyroscope(gx: Float, gy: Float, gz: Float) {
        binding.tvGyroX.text = String.format("%+.3f", gx)
        binding.tvGyroY.text = String.format("%+.3f", gy)
        binding.tvGyroZ.text = String.format("%+.3f", gz)

        binding.progressGyroX.progress = gyroToProgress(gx)
        binding.progressGyroY.progress = gyroToProgress(gy)
        binding.progressGyroZ.progress = gyroToProgress(gz)
    }

    private fun updateGpsStrip(speedKmh: Float, gpsLocked: Boolean) {
        if (gpsLocked) {
            binding.tvGpsStatus.text = "📡 GPS: Locked ✅"
            binding.tvGpsStatus.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_success)
            )
        } else {
            binding.tvGpsStatus.text = "📡 GPS: Acquiring..."
            binding.tvGpsStatus.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_warning)
            )
        }
        binding.tvSpeedKmh.text = if (speedKmh >= 0f)
            String.format("%.1f km/h", speedKmh)
        else
            "-- km/h"
    }

    /** Maps -20..+20 m/s² → 0..200 (100 = zero) */
    private fun axisToProgress(value: Float): Int =
        (100 + (value / 20f) * 100).toInt().coerceIn(0, 200)

    /** Maps -10..+10 rad/s → 0..200 (100 = zero) */
    private fun gyroToProgress(value: Float): Int =
        (100 + (value / 10f) * 100).toInt().coerceIn(0, 200)
}
