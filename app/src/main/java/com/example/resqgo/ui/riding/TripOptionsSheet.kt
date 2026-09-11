package com.example.resqgo.ui.riding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.resqgo.R
import com.example.resqgo.databinding.SheetTripOptionsBinding
import com.example.resqgo.navigation.RoutePreferences
import com.example.resqgo.navigation.TravelMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * The three-dot Trip / Route Options sheet (Change 10).
 *
 * Owns nothing: it edits a copy of [RoutePreferences] and hands the result back
 * through [listener], so RidingActivity stays the single source of truth for the
 * active journey.
 */
class TripOptionsSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onPreferencesChanged(prefs: RoutePreferences)
        fun onAddStopRequested()
        fun onShareRouteRequested()
        fun onSaveRouteRequested()
        fun onStartRequested()
    }

    private var _binding: SheetTripOptionsBinding? = null
    private val binding get() = _binding!!

    var listener: Listener? = null
    private var current: RoutePreferences = RoutePreferences()
    private var stopsLabel: String? = null

    fun setInitialState(prefs: RoutePreferences, stops: String?) {
        current = prefs
        stopsLabel = stops
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetTripOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chipGroupMode.check(
            when (current.travelMode) {
                TravelMode.TWO_WHEELER -> R.id.chipModeBike
                TravelMode.CAR -> R.id.chipModeCar
                TravelMode.TRANSIT -> R.id.chipModeTransit
                TravelMode.WALKING -> R.id.chipModeWalk
            }
        )
        binding.swAvoidTolls.isChecked = current.avoidTolls
        binding.swAvoidHighways.isChecked = current.avoidHighways
        binding.swAvoidFerries.isChecked = current.avoidFerries
        binding.swFuelEfficient.isChecked = current.fuelEfficient
        binding.tvStopsSummary.text = stopsLabel ?: getString(R.string.trip_no_stops)

        binding.chipGroupMode.setOnCheckedStateChangeListener { _, ids ->
            val mode = when (ids.firstOrNull()) {
                R.id.chipModeCar -> TravelMode.CAR
                R.id.chipModeTransit -> TravelMode.TRANSIT
                R.id.chipModeWalk -> TravelMode.WALKING
                else -> TravelMode.TWO_WHEELER
            }
            current = current.copy(travelMode = mode)
            publish()
        }

        binding.swAvoidTolls.setOnCheckedChangeListener { _, v ->
            current = current.copy(avoidTolls = v); publish()
        }
        binding.swAvoidHighways.setOnCheckedChangeListener { _, v ->
            current = current.copy(avoidHighways = v); publish()
        }
        binding.swAvoidFerries.setOnCheckedChangeListener { _, v ->
            current = current.copy(avoidFerries = v); publish()
        }
        binding.swFuelEfficient.setOnCheckedChangeListener { _, v ->
            current = current.copy(fuelEfficient = v); publish()
        }

        binding.btnAddStop.setOnClickListener { listener?.onAddStopRequested(); dismiss() }
        binding.btnShareRoute.setOnClickListener { listener?.onShareRouteRequested() }
        binding.btnSaveRoute.setOnClickListener { listener?.onSaveRouteRequested() }
        binding.btnApplyAndStart.setOnClickListener { listener?.onStartRequested(); dismiss() }
    }

    /** Routes recalculate live as preferences change, per the spec. */
    private fun publish() = listener?.onPreferencesChanged(current)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
