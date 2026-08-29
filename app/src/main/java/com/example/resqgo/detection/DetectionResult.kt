package com.example.resqgo.detection

sealed class DetectionResult {
    object Safe : DetectionResult()
    data class PossibleAccident(val confidence: Float, val reason: String) : DetectionResult()
    object DefiniteAccident : DetectionResult()
}
