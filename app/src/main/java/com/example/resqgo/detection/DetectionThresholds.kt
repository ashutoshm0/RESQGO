package com.example.resqgo.detection

object DetectionThresholds {
    /**
     * CALIBRATED FOR INDIAN ROAD CONDITIONS
     *
     * Indian road data (accelerometer total magnitude, m/s²):
     *   - Normal riding on smooth road:  ~9.8  m/s²  (1.0G — gravity only)
     *   - Pothole / rough road:         ~15–20 m/s²  (1.5–2.0G)
     *   - Speed breaker at speed:       ~20–25 m/s²  (2.0–2.5G)
     *   - Hard braking:                 ~15–18 m/s²
     *   - Actual crash (motorcycle):    ~30–60 m/s²  (3.0–6.0G)
     *
     * Therefore safe thresholds that avoid false alarms on Indian roads:
     */

    // HIGH: 2.8G — still catches serious crashes, avoids big potholes/speedbreakers
    const val IMPACT_HIGH_SENSITIVITY   = 27.4f   // 2.8G

    // MEDIUM: 3.5G — recommended for Indian city roads (default)
    const val IMPACT_MEDIUM_SENSITIVITY = 34.3f   // 3.5G

    // LOW: 4.5G — only catastrophic crashes (highway use)
    const val IMPACT_LOW_SENSITIVITY    = 44.1f   // 4.5G

    // Default (used if prefs not loaded)
    const val IMPACT_G_FORCE_THRESHOLD  = IMPACT_MEDIUM_SENSITIVITY

    // Stillness window after impact: phone must be still for this long
    const val STILLNESS_DURATION_MS     = 3000L   // 3 seconds

    // Stillness = magnitude within ±2.0 m/s² of 9.8 (gravity at rest)
    const val STILLNESS_G_FORCE_VARIANCE = 2.0f

    fun getThreshold(sensitivity: String): Float = when (sensitivity.uppercase()) {
        "HIGH"   -> IMPACT_HIGH_SENSITIVITY
        "LOW"    -> IMPACT_LOW_SENSITIVITY
        else     -> IMPACT_MEDIUM_SENSITIVITY  // MEDIUM
    }

    /** Human-readable description for each level */
    fun getDescription(sensitivity: String): String = when (sensitivity.uppercase()) {
        "HIGH"   -> "HIGH — Detects serious crashes (2.8G). May occasionally trigger on large potholes at speed."
        "LOW"    -> "LOW — Only catastrophic crashes (4.5G). Best for highway/expressway riding."
        else     -> "MEDIUM — Recommended for Indian city roads (3.5G). Avoids speed-breaker false alarms."
    }
}
