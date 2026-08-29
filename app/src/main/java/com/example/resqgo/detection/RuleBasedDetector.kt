package com.example.resqgo.detection

import com.example.resqgo.sensors.AccelerometerData
import com.example.resqgo.sensors.GyroscopeData

class RuleBasedDetector(private val impactThreshold: Float = DetectionThresholds.IMPACT_G_FORCE_THRESHOLD) {
    private var hasRecentImpact = false
    private var impactTimestamp = 0L

    fun analyze(accel: AccelerometerData?, gyro: GyroscopeData?, speedMps: Float?): DetectionResult {
        if (accel == null) return DetectionResult.Safe
        val gForce = accel.magnitude()
        val currentTime = accel.timestamp

        // Only set timestamp on FIRST impact frame (not repeatedly)
        if (gForce > impactThreshold && !hasRecentImpact) {
            hasRecentImpact = true
            impactTimestamp = currentTime
        }

        if (hasRecentImpact) {
            val timeSinceImpact = currentTime - impactTimestamp
            if (timeSinceImpact > DetectionThresholds.STILLNESS_DURATION_MS) {
                val isStill = Math.abs(gForce - 9.8f) < DetectionThresholds.STILLNESS_G_FORCE_VARIANCE
                hasRecentImpact = false
                if (isStill) {
                    return DetectionResult.PossibleAccident(0.9f, "High impact followed by stillness")
                }
            }
        }
        return DetectionResult.Safe
    }
    
    fun reset() {
        hasRecentImpact = false
        impactTimestamp = 0L
    }
}
