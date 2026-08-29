package com.example.resqgo.sensors

data class GyroscopeData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
) {
    // Calculates the angular velocity magnitude
    fun magnitude(): Float {
        return kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }
}
