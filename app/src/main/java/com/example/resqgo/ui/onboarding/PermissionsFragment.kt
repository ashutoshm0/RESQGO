package com.example.resqgo.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.FragmentPermissionsBinding
import com.example.resqgo.ui.home.HomeActivity
import com.example.resqgo.util.PermissionHelper

class PermissionsFragment : Fragment() {
    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        completeOnboarding()
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        maybeRequestBackgroundLocation()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAllow.setOnClickListener {
            foregroundPermissionLauncher.launch(PermissionHelper.startupPermissions())
        }
    }

    /**
     * Android 11+ auto-denies ACCESS_BACKGROUND_LOCATION when it is asked for in the
     * same breath as foreground location, and refuses it outright when foreground
     * location was denied. Only ask once foreground location is actually granted,
     * and explain what it buys the rider first.
     */
    private fun maybeRequestBackgroundLocation() {
        val ctx = context ?: return
        val needsBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            PermissionHelper.hasLocation(ctx) &&
            !PermissionHelper.hasBackgroundLocation(ctx)

        if (!needsBackground) {
            completeOnboarding()
            return
        }

        AlertDialog.Builder(ctx)
            .setTitle(R.string.background_location_title)
            .setMessage(R.string.background_location_message)
            .setPositiveButton(R.string.allow_all_the_time) { _, _ ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.not_now) { _, _ -> completeOnboarding() }
            .setCancelable(false)
            .show()
    }

    private fun completeOnboarding() {
        val activity = activity ?: return
        UserPreferences(activity).hasCompletedOnboarding = true
        startActivity(Intent(activity, HomeActivity::class.java))
        activity.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
