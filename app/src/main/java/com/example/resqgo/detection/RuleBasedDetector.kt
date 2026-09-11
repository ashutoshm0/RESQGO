package com.example.resqgo.detection

import com.example.resqgo.sensors.AccelerometerData
import com.example.resqgo.sensors.GyroscopeData
import kotlin.math.pow

class RuleBasedDetector(private val impactThreshold: Float = DetectionThresholds.IMPACT_G_FORCE_THRESHOLD) {
    
    private enum class State {
        MONITORING,
        BUFFERING
    }
    
    private var currentState = State.MONITORING
    private var impactTimestamp = 0L
    private val accelBuffer = mutableListOf<Float>()

    fun analyze(accel: AccelerometerData?, gyro: GyroscopeData?, speedMps: Float?): DetectionResult {
        if (accel == null) return DetectionResult.Safe
        
        val gForce = accel.magnitude()
        val currentTime = accel.timestamp

        when (currentState) {
            State.MONITORING -> {
                val gyroMag = gyro?.magnitude() ?: 0f
                val isImpact = gForce > impactThreshold
                val isRotation = gyroMag > DetectionThresholds.GYROSCOPE_ROTATION_THRESHOLD
                val isExtreme = gForce > DetectionThresholds.EXTREME_IMPACT_THRESHOLD
                
                // Stage 1: Impact + Rotation (or Extreme Impact)
                if (isImpact && (isRotation || isExtreme)) {
                    currentState = State.BUFFERING
                    impactTimestamp = currentTime
                    accelBuffer.clear()
                    // NB: the impact sample is deliberately NOT buffered. A 40-70 m/s²
                    // spike sitting in the buffer dominates the variance and makes the
                    // stillness test unpassable — the harder the crash, the surer the miss.
                }
            }
            State.BUFFERING -> {
                val timeSinceImpact = currentTime - impactTimestamp

                // Only sample once the crash dynamics have settled.
                if (timeSinceImpact >= DetectionThresholds.SETTLE_DELAY_MS) {
                    accelBuffer.add(gForce)
                }

                if (timeSinceImpact > DetectionThresholds.STILLNESS_BUFFER_DURATION_MS) {
                    // Time to evaluate the buffer!
                    val variance = calculateVariance(accelBuffer)
                    val currentSpeed = speedMps ?: 0f
                    
                    // Stage 3: Statistical Evaluation.
                    // An empty/short buffer means we never got readings in the settle
                    // window — treat that as inconclusive rather than as stillness,
                    // otherwise a sensor stall reads as a crash.
                    val haveEnough = accelBuffer.size >= DetectionThresholds.MIN_STILLNESS_SAMPLES
                    val isStill = haveEnough && variance < DetectionThresholds.STILLNESS_G_FORCE_VARIANCE
                    val isStopped = currentSpeed <= DetectionThresholds.MAX_POST_CRASH_SPEED_MPS
                    
                    val result = if (isStill && isStopped) {
                        DetectionResult.PossibleAccident(0.9f, "Multi-signal crash: impact + rotation + stillness + stopped")
                    } else {
                        // Dismiss as pothole/rough road
                        DetectionResult.Safe
                    }
                    
                    // Reset for next event
                    reset()
                    return result
                }
            }
        }
        
        return DetectionResult.Safe
    }
    
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.sum() / values.size
        val variance = values.map { (it - mean).pow(2) }.sum() / values.size
        return variance
    }
    
    fun reset() {
        currentState = State.MONITORING
        impactTimestamp = 0L
        accelBuffer.clear()
    }
}
