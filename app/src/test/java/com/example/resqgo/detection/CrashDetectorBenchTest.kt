package com.example.resqgo.detection

import com.example.resqgo.sensors.AccelerometerData
import com.example.resqgo.sensors.GyroscopeData
import org.junit.Test
import java.io.File
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Bench for [RuleBasedDetector].
 *
 * Replays synthetic riding traces (sampled at 50 Hz, the rate a crash impulse
 * actually needs) through the real detector and compares it against the naive
 * single-threshold detector that a first version would use. Produces the
 * false-positive / miss counts written to build/reports/detector-bench.txt.
 *
 * Trace amplitudes follow the bands documented in [DetectionThresholds]:
 * smooth road ~1 G, pothole 1.5-2.5 G, speed breaker 2.5-3.5 G, crash 3.8-6 G+.
 */
class CrashDetectorBenchTest {

    private var hz = 50
    private var dtMs = 1000L / 50
    private val g = 9.81f

    private data class Sample(
        val ax: Float, val ay: Float, val az: Float,
        val gx: Float, val gy: Float, val gz: Float,
        val speedMps: Float, val t: Long
    )

    private data class Scenario(val name: String, val isCrash: Boolean, val samples: List<Sample>)

    private var rng = Random(20260907)

    private fun noise(scale: Float) = (rng.nextFloat() - 0.5f) * 2f * scale

    /** Builds one event: [preS] seconds of riding, an impulse, then [postS] seconds. */
    private fun trace(
        name: String,
        isCrash: Boolean,
        peakG: Float,
        peakRotRad: Float,
        impulseMs: Long,
        rideSpeed: Float,
        postSpeed: Float,
        postAccelNoise: Float,
        postRotNoise: Float,
        preS: Double = 2.0,
        postS: Double = 5.0
    ): Scenario {
        val samples = mutableListOf<Sample>()
        var t = 0L

        // Pre-impact: normal riding
        repeat((preS * hz).toInt()) {
            samples += Sample(
                noise(0.7f), noise(0.7f), g + noise(0.7f),
                noise(0.35f), noise(0.35f), noise(0.2f),
                rideSpeed, t
            )
            t += dtMs
        }

        // Impulse: half-sine rise and fall to the peak
        val impulseSamples = (impulseMs / dtMs).toInt().coerceAtLeast(1)
        for (i in 0 until impulseSamples) {
            val phase = Math.sin(Math.PI * i / impulseSamples).toFloat()
            val mag = g + (peakG - g) * phase
            val rot = peakRotRad * phase
            samples += Sample(
                noise(1.2f), noise(1.2f), mag,
                rot * 0.6f + noise(0.2f), rot * 0.7f + noise(0.2f), rot * 0.3f + noise(0.2f),
                rideSpeed, t
            )
            t += dtMs
        }

        // Post-impact: either the rider carries on, or the bike is down and still
        val speedStep = (rideSpeed - postSpeed) / (0.6 * hz).toFloat()
        var speed = rideSpeed
        repeat((postS * hz).toInt()) {
            speed = if (postSpeed < rideSpeed) (speed - speedStep).coerceAtLeast(postSpeed) else rideSpeed
            samples += Sample(
                noise(postAccelNoise), noise(postAccelNoise), g + noise(postAccelNoise),
                noise(postRotNoise), noise(postRotNoise), noise(postRotNoise),
                speed, t
            )
            t += dtMs
        }
        return Scenario(name, isCrash, samples)
    }

    /** Same events every time, re-sampled at [rateHz]. */
    private fun buildSuite(rateHz: Int): List<Scenario> {
        hz = rateHz
        dtMs = (1000.0 / rateHz).toLong().coerceAtLeast(1L)
        rng = Random(20260907)
        val out = mutableListOf<Scenario>()

        // ── NON-CRASH events ────────────────────────────────────────────────
        // Pothole: 1.5-2.5 G, mild rotation, rider rides on.
        repeat(60) {
            out += trace(
                "pothole", false,
                peakG = 15f + rng.nextFloat() * 10f,
                peakRotRad = 0.8f + rng.nextFloat() * 1.0f,
                impulseMs = 120, rideSpeed = 7f + rng.nextFloat() * 4f, postSpeed = 7f,
                postAccelNoise = 1.6f, postRotNoise = 0.6f
            )
        }
        // Speed breaker taken at speed: 2.5-3.5 G.
        repeat(45) {
            out += trace(
                "speed_breaker", false,
                peakG = 25f + rng.nextFloat() * 10f,
                peakRotRad = 1.2f + rng.nextFloat() * 0.9f,
                impulseMs = 180, rideSpeed = 6f + rng.nextFloat() * 4f, postSpeed = 6f,
                postAccelNoise = 1.5f, postRotNoise = 0.6f
            )
        }
        // Kerb strike / deep pothole at speed: crosses BOTH impact and rotation gates,
        // but the rider keeps riding. This is what stage 3 exists to reject.
        repeat(20) {
            out += trace(
                "kerb_strike", false,
                peakG = 32f + rng.nextFloat() * 8f,
                peakRotRad = 2.4f + rng.nextFloat() * 1.2f,
                impulseMs = 140, rideSpeed = 8f + rng.nextFloat() * 4f, postSpeed = 8f,
                postAccelNoise = 1.8f, postRotNoise = 0.7f
            )
        }
        // Hard emergency braking: sustained force, rider stops, but no rotation.
        repeat(30) {
            out += trace(
                "hard_braking", false,
                peakG = 14f + rng.nextFloat() * 6f,
                peakRotRad = 0.5f + rng.nextFloat() * 0.5f,
                impulseMs = 800, rideSpeed = 11f, postSpeed = 0f,
                postAccelNoise = 0.5f, postRotNoise = 0.25f
            )
        }
        // Rough gravel stretch: continuous moderate shaking.
        repeat(25) {
            out += trace(
                "rough_road", false,
                peakG = 12f + rng.nextFloat() * 6f,
                peakRotRad = 0.9f + rng.nextFloat() * 0.8f,
                impulseMs = 600, rideSpeed = 5f + rng.nextFloat() * 3f, postSpeed = 5f,
                postAccelNoise = 2.2f, postRotNoise = 0.9f
            )
        }

        // ── CRASH events ────────────────────────────────────────────────────
        // Lowside: bike slides out, rider down and still.
        repeat(20) {
            out += trace(
                "crash_lowside", true,
                peakG = 40f + rng.nextFloat() * 12f,
                peakRotRad = 3.5f + rng.nextFloat() * 2.5f,
                impulseMs = 160, rideSpeed = 9f + rng.nextFloat() * 5f, postSpeed = 0f,
                postAccelNoise = 0.25f, postRotNoise = 0.12f
            )
        }
        // Highside / tumble: violent rotation.
        repeat(10) {
            out += trace(
                "crash_highside", true,
                peakG = 55f + rng.nextFloat() * 15f,
                peakRotRad = 6f + rng.nextFloat() * 3f,
                impulseMs = 200, rideSpeed = 12f + rng.nextFloat() * 6f, postSpeed = 0f,
                postAccelNoise = 0.3f, postRotNoise = 0.15f
            )
        }
        // Head-on / collision with a vehicle: extreme impact, less rotation.
        repeat(10) {
            out += trace(
                "crash_headon", true,
                peakG = 60f + rng.nextFloat() * 20f,
                peakRotRad = 1.5f + rng.nextFloat() * 1.0f,
                impulseMs = 100, rideSpeed = 13f + rng.nextFloat() * 5f, postSpeed = 0f,
                postAccelNoise = 0.22f, postRotNoise = 0.1f
            )
        }
        return out
    }

    /** The current shipping detector. */
    private fun runRuleBased(s: Scenario, threshold: Float): Boolean {
        val det = RuleBasedDetector(threshold)
        for (smp in s.samples) {
            val r = det.analyze(
                AccelerometerData(smp.ax, smp.ay, smp.az, smp.t),
                GyroscopeData(smp.gx, smp.gy, smp.gz, smp.t),
                smp.speedMps
            )
            if (r is DetectionResult.PossibleAccident) return true
        }
        return false
    }

    /** Naive v1: fire the moment any sample crosses the G threshold. */
    private fun runNaive(s: Scenario, threshold: Float): Boolean =
        s.samples.any { sqrt(it.ax * it.ax + it.ay * it.ay + it.az * it.az) > threshold }

    @Test
    fun bench() {
        val suite = buildSuite(50)
        val sb = StringBuilder()
        sb.appendLine("RESQGO crash-detector bench — ${suite.size} synthetic events @ ${hz} Hz")
        sb.appendLine("=".repeat(78))

        val levels = listOf(
            "HIGH" to DetectionThresholds.IMPACT_HIGH_SENSITIVITY,
            "MEDIUM" to DetectionThresholds.IMPACT_MEDIUM_SENSITIVITY,
            "LOW" to DetectionThresholds.IMPACT_LOW_SENSITIVITY
        )

        for ((label, thr) in levels) {
            val crashes = suite.filter { it.isCrash }
            val benign = suite.filter { !it.isCrash }

            val naiveFp = benign.count { runNaive(it, thr) }
            val naiveTp = crashes.count { runNaive(it, thr) }
            val ruleFp = benign.count { runRuleBased(it, thr) }
            val ruleTp = crashes.count { runRuleBased(it, thr) }

            sb.appendLine()
            sb.appendLine("--- $label sensitivity (${"%.2f".format(thr / g)} G / $thr m/s²) ---")
            sb.appendLine("                       false alarms      crashes caught")
            sb.appendLine("  naive threshold        %3d / %3d          %3d / %3d"
                .format(naiveFp, benign.size, naiveTp, crashes.size))
            sb.appendLine("  3-stage detector       %3d / %3d          %3d / %3d"
                .format(ruleFp, benign.size, ruleTp, crashes.size))

            sb.appendLine("  per-event-type false alarms (3-stage):")
            benign.groupBy { it.name }.toSortedMap().forEach { (name, group) ->
                val fp = group.count { runRuleBased(it, thr) }
                sb.appendLine("      %-16s %2d / %2d".format(name, fp, group.size))
            }
            sb.appendLine("  crash recall by type (3-stage):")
            crashes.groupBy { it.name }.toSortedMap().forEach { (name, group) ->
                val tp = group.count { runRuleBased(it, thr) }
                sb.appendLine("      %-16s %2d / %2d".format(name, tp, group.size))
            }
        }

        // Per-sample cost of the detector.
        val stream = suite.flatMap { it.samples }
        val det = RuleBasedDetector(DetectionThresholds.IMPACT_MEDIUM_SENSITIVITY)
        repeat(3) { // warm up the JIT
            stream.forEach {
                det.analyze(
                    AccelerometerData(it.ax, it.ay, it.az, it.t),
                    GyroscopeData(it.gx, it.gy, it.gz, it.t), it.speedMps
                )
            }
        }
        val start = System.nanoTime()
        stream.forEach {
            det.analyze(
                AccelerometerData(it.ax, it.ay, it.az, it.t),
                GyroscopeData(it.gx, it.gy, it.gz, it.t), it.speedMps
            )
        }
        val nsPerSample = (System.nanoTime() - start).toDouble() / stream.size

        sb.appendLine()
        sb.appendLine("--- cost ---")
        sb.appendLine("  samples processed : ${stream.size}")
        sb.appendLine("  per sample        : %.3f µs".format(nsPerSample / 1000.0))
        sb.appendLine("  budget @ 50 Hz    : 20000 µs  (headroom %.0fx)"
            .format(20_000.0 / (nsPerSample / 1000.0)))

        // ── Sampling-rate sweep ─────────────────────────────────────────────
        sb.appendLine()
        sb.appendLine("--- sampling rate vs. crash recall (MEDIUM sensitivity) ---")
        sb.appendLine("  Android constant       rate     crashes caught   false alarms")
        val rates = listOf(
            "SENSOR_DELAY_NORMAL" to 5,
            "SENSOR_DELAY_UI" to 15,
            "SENSOR_DELAY_GAME" to 50,
            "SENSOR_DELAY_FASTEST" to 100
        )
        for ((constant, rate) in rates) {
            val s2 = buildSuite(rate)
            val c = s2.filter { it.isCrash }
            val b = s2.filter { !it.isCrash }
            val tp = c.count { runRuleBased(it, DetectionThresholds.IMPACT_MEDIUM_SENSITIVITY) }
            val fp = b.count { runRuleBased(it, DetectionThresholds.IMPACT_MEDIUM_SENSITIVITY) }
            sb.appendLine("  %-22s %3d Hz      %3d / %3d        %3d / %3d"
                .format(constant, rate, tp, c.size, fp, b.size))
        }

        val report = sb.toString()
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/detector-bench.txt").writeText(report)
    }
}
