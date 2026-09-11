package com.example.resqgo.ui.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivitySplashBinding
import com.example.resqgo.detection.DetectionThresholds
import com.example.resqgo.detection.RuleBasedDetector
import com.example.resqgo.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Branded startup surface (Change 7).
 *
 * The old implementation slept for a flat 1500 ms and did nothing. The spec is
 * explicit: use the startup window for real initialisation and do not impose an
 * artificial wait. Every step below is genuine work, the status line names what
 * is happening, and we leave the moment the work finishes.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    /** Enough time for the entrance animation to read — nothing beyond it. */
    private val minimumOnScreenMs = 700L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playEntrance()
        runStartupInitialisation()
    }

    // ── Branded entrance ─────────────────────────────────────────────────────

    private fun playEntrance() {
        binding.imgChakra.animate()
            .alpha(0.5f).setDuration(650).setInterpolator(DecelerateInterpolator()).start()

        binding.imgSplashMark.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(520).setInterpolator(OvershootInterpolator(1.6f)).start()

        binding.wordmark.animate()
            .alpha(1f).setStartDelay(220).setDuration(420).start()

        binding.tvTagline.animate()
            .alpha(1f).setStartDelay(360).setDuration(420).start()
    }

    // ── Real initialisation ──────────────────────────────────────────────────

    private data class Step(val labelRes: Int, val work: suspend () -> Unit)

    private fun runStartupInitialisation() {
        val prefs = UserPreferences(this)

        val steps = listOf(
            Step(R.string.init_sensors) {
                val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val hasAccel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                val hasGyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                prefs.sensorsAvailable = hasAccel && hasGyro
            },
            Step(R.string.init_gps) {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                prefs.gpsReady = runCatching {
                    lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                }.getOrDefault(false)
            },
            Step(R.string.init_model) {
                // Warms the detector and its thresholds so the first sensor
                // event during a ride is not the one paying class-load cost.
                RuleBasedDetector(DetectionThresholds.getThreshold(prefs.sensitivity)).reset()
            },
            Step(R.string.init_settings) {
                prefs.getEmergencyContacts()
                prefs.sosTimerSeconds
                prefs.riderName
            },
            Step(R.string.init_maps) {
                // osmdroid is configured in ResqgoApp; make sure its tile cache
                // directory exists now so the first map frame is not doing disk
                // setup on the main thread.
                runCatching {
                    org.osmdroid.config.Configuration.getInstance()
                        .osmdroidTileCache?.mkdirs()
                }
            }
        )

        lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()

            steps.forEachIndexed { index, step ->
                binding.tvInitStatus.setText(step.labelRes)
                withContext(Dispatchers.Default) { runCatching { step.work() } }
                binding.progressInit.setProgressCompat(
                    ((index + 1) * 100) / steps.size, true
                )
            }

            binding.tvInitStatus.setText(R.string.init_ready)

            // Only wait if the animation has not finished playing — never longer.
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minimumOnScreenMs) delay(minimumOnScreenMs - elapsed)

            val next = if (prefs.hasCompletedOnboarding) HomeActivity::class.java
            else OnboardingActivity::class.java
            startActivity(Intent(this@SplashActivity, next))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
