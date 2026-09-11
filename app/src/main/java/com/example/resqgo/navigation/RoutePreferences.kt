package com.example.resqgo.navigation

import com.example.resqgo.R

/**
 * Travel modes offered in Trip Options (Change 10).
 * Two-wheeler is the default and the most prominent — RESQGO is built for riders.
 */
enum class TravelMode(
    val labelRes: Int,
    /** Assumed average speed, km/h, used by the local ETA estimator. */
    val avgSpeedKmh: Float
) {
    TWO_WHEELER(R.string.mode_two_wheeler, 35f),
    CAR(R.string.mode_car, 28f),
    TRANSIT(R.string.mode_transit, 20f),
    WALKING(R.string.mode_walking, 4.8f)
}

/**
 * Route characteristics the rider can compare. These survive from the original
 * four buttons but are now attributes of a route rather than the whole UI.
 */
enum class RouteFlavour(
    val labelRes: Int,
    /** Multiplier on straight-line distance. */
    val distanceFactor: Float,
    /** Multiplier on the travel mode's average speed. */
    val speedFactor: Float,
    val colorHex: String,
    val hasTolls: Boolean,
    val usesHighway: Boolean
) {
    RECOMMENDED(R.string.nav_route_better, 1.00f, 1.00f, "#805AD5", hasTolls = true, usesHighway = true),
    SHORTEST(R.string.nav_route_shortest, 0.92f, 0.80f, "#3182CE", hasTolls = false, usesHighway = false),
    TOLL_FREE(R.string.nav_route_toll_free, 1.05f, 0.92f, "#D2691E", hasTolls = false, usesHighway = true),
    LOW_TRAFFIC(R.string.nav_route_low_traffic, 1.10f, 1.20f, "#38A169", hasTolls = false, usesHighway = false)
}

/**
 * The rider's trip preferences. Applied to every route estimate and persisted so
 * a ride resumes with the same choices.
 *
 * NOTE: these bias RESQGO's local estimator. Real avoid-tolls / live-traffic data
 * requires the Google Directions API; wiring that in only changes where the
 * distance and duration numbers come from, not this model.
 */
data class RoutePreferences(
    val travelMode: TravelMode = TravelMode.TWO_WHEELER,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val fuelEfficient: Boolean = false
) {
    /** True when this flavour is compatible with the rider's stated preferences. */
    fun permits(flavour: RouteFlavour): Boolean = when {
        avoidTolls && flavour.hasTolls -> false
        avoidHighways && flavour.usesHighway -> false
        else -> true
    }

    /** Extra distance the preferences cost, as a multiplier. */
    fun distancePenalty(): Float {
        var f = 1f
        if (avoidTolls) f *= 1.05f
        if (avoidHighways) f *= 1.12f
        if (avoidFerries) f *= 1.02f
        if (fuelEfficient) f *= 0.98f
        return f
    }

    /** Fuel-efficient routing trades a little speed for smoother running. */
    fun speedPenalty(): Float = if (fuelEfficient) 0.93f else 1f
}
