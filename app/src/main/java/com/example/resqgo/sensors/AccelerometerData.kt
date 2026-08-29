package com.example.resqgo.sensors

data class AccelerometerData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
) {
    // Calculates the magnitude of the acceleration vector (g-force)
    fun magnitude(): Float {
        return kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }
}
