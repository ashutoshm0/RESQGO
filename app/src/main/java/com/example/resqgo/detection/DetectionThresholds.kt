package com.example.resqgo.detection

object DetectionThresholds {
    /**
     * CALIBRATED FOR INDIAN ROAD CONDITIONS
     *
     * Indian road data (accelerometer total magnitude, m/s²):
     *   - Normal riding on smooth road:  ~9.8  m/s²  (1.0G — gravity only)
     *   - Pothole / rough road:         ~15—25 m/s²  (1.5—2.5G)
     *   - Speed breaker at speed:       ~25—35 m/s²  (2.5—3.5G)
     *   - Actual crash (motorcycle):    ~38—60+ m/s² (3.8—6.0G+)
     */

    // HIGH: 3.2G — more sensitive, might still catch big potholes
    const val IMPACT_HIGH_SENSITIVITY   = 31.3f   // 3.2G

    // MEDIUM: 3.87G — recommended for Indian city roads
    const val IMPACT_MEDIUM_SENSITIVITY = 38.0f   // 3.87G

    // LOW: 4.8G — only catastrophic crashes (highway use)
    const val IMPACT_LOW_SENSITIVITY    = 47.0f   // 4.8G

    // Default
    const val IMPACT_G_FORCE_THRESHOLD  = IMPACT_MEDIUM_SENSITIVITY

    // Multi-signal validation thresholds
    const val GYROSCOPE_ROTATION_THRESHOLD = 2.2f     // rad/s (tumbling)
    const val EXTREME_IMPACT_THRESHOLD = 55.0f        // m/s² (5.6G, skip gyro check if this high)
    
    // Stillness evaluation after impact
    const val STILLNESS_BUFFER_DURATION_MS = 3500L    // 3.5 seconds
    const val STILLNESS_G_FORCE_VARIANCE = 0.8f       // Variance must be low (phone is lying still)
    const val MAX_POST_CRASH_SPEED_MPS = 1.0f         // Max 3.6 km/h post-crash

    fun getThreshold(sensitivity: String): Float = when (sensitivity.uppercase()) {
        "HIGH"   -> IMPACT_HIGH_SENSITIVITY
        "LOW"    -> IMPACT_LOW_SENSITIVITY
        else     -> IMPACT_MEDIUM_SENSITIVITY  // MEDIUM
    }

    /** Human-readable description for each level */
    fun getDescription(sensitivity: String): String = when (sensitivity.uppercase()) {
        "HIGH"   -> "HIGH — Detects serious crashes (3.2G). May occasionally trigger on large potholes at speed."
        "LOW"    -> "LOW — Only catastrophic crashes (4.8G). Best for highway/expressway riding."
        else     -> "MEDIUM — Recommended for Indian city roads (3.8G). Avoids speed-breaker false alarms."
    }
}
