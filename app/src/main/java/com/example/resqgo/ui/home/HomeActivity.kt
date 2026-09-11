package com.example.resqgo.ui.home

import android.content.Intent
import android.os.Bundle
import com.example.resqgo.util.applySystemBarPadding
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.ThemeMode
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivityHomeBinding
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import com.example.resqgo.ui.contacts.EmergencyContactsActivity
import com.example.resqgo.ui.history.RideHistoryActivity
import com.example.resqgo.ui.riding.RidingActivity
import com.example.resqgo.ui.settings.SettingsActivity
import com.example.resqgo.util.PermissionHelper
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: UserPreferences
    private lateinit var sosManager: SOSManager

    /** Set when the rider tapped START RIDE but we had to ask for permissions first. */
    private var startRideAfterGrant = false

    /** Whether the pending ride should open the destination search. */
    private var pendingWithDestination = true

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Granted or not, the ride goes ahead — SOS just falls back a layer.
        doStartRide()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateStatus()
        if (startRideAfterGrant) {
            startRideAfterGrant = false
            startRide()
        }
    }

    companion object {
        const val ACTION_SENSOR_UPDATE = "com.example.resqgo.SENSOR_UPDATE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        prefs = UserPreferences(this)
        sosManager = SOSManager(this)

        setupListeners()
        updateThemeToggleIcon()

        // Ask once on entry so the rider is never sent to system Settings to
        // recover from a silent failure later on.
        if (PermissionHelper.missingForRide(this).isNotEmpty()) {
            permissionLauncher.launch(PermissionHelper.startupPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
        updateContactsCard()
        updateStatus()
    }

    private fun updateGreeting() {
        val name = prefs.riderName
        val timeOfDay = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11  -> getString(R.string.greeting_morning)
            in 12..16 -> getString(R.string.greeting_afternoon)
            else      -> getString(R.string.greeting_evening)
        }
        binding.tvGreeting.text = timeOfDay
        binding.tvRiderName.text = if (!name.isNullOrBlank()) name else getString(R.string.rider_default_name)
    }

    private fun updateContactsCard() {
        val contacts = prefs.getEmergencyContacts()
        if (contacts.isEmpty()) {
            binding.tvContactsSummary.text = getString(R.string.no_contacts)
            binding.tvContactsHint.text = getString(R.string.contacts_hint_empty)
            binding.tvContactsHint.visibility = View.VISIBLE
        } else {
            binding.tvContactsSummary.text = contacts.joinToString("\n") { "• ${it.name}  ${it.phone}" }
            binding.tvContactsHint.visibility = View.GONE
        }
    }

    private fun updateStatus() {
        if (prefs.isRidingOnline) {
            binding.tvSafetyStatus.text = getString(R.string.status_monitoring)
            binding.tvSafetyStatus.setTextColor(ContextCompat.getColor(this, R.color.resqgo_success))
            binding.cardActiveBanner.visibility = View.VISIBLE
            binding.btnStartRide.text = getString(R.string.btn_return_to_ride)
        } else {
            binding.tvSafetyStatus.text = getString(R.string.status_ready)
            binding.tvSafetyStatus.setTextColor(ContextCompat.getColor(this, R.color.resqgo_text_secondary))
            binding.cardActiveBanner.visibility = View.GONE
            binding.btnStartRide.text = getString(R.string.btn_start_ride)
        }
        binding.tvCrashTimerInfo.text =
            getString(R.string.auto_sos_timer_info, formatTimer(prefs.sosTimerSeconds))

        // Surface a permission warning inline instead of failing at ride start.
        val missing = PermissionHelper.missingForRide(this)
        if (missing.isEmpty()) {
            binding.cardPermissionWarning.visibility = View.GONE
        } else {
            binding.cardPermissionWarning.visibility = View.VISIBLE
            binding.tvPermissionWarning.text =
                getString(R.string.permission_warning, missing.joinToString(", "))
        }
    }

    private fun setupListeners() {
        binding.btnStartRide.setOnClickListener {
            if (prefs.isRidingOnline) {
                // Already riding — go back to riding screen
                startActivity(Intent(this, RidingActivity::class.java))
            } else {
                startRide(withDestination = true)
            }
        }

        // Change 9: safety monitoring must not require a destination.
        binding.btnStartRideNoDest.setOnClickListener {
            if (prefs.isRidingOnline) {
                startActivity(Intent(this, RidingActivity::class.java))
            } else {
                startRide(withDestination = false)
            }
        }

        binding.btnManualSOS.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.sos_confirm_title))
                .setMessage(getString(R.string.sos_confirm_message))
                .setPositiveButton(getString(R.string.yes_send_sos)) { _, _ ->
                    sosManager.triggerSOS(manuallyTriggered = true)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.btnManageContacts.setOnClickListener {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        }

        binding.cardActiveBanner.setOnClickListener {
            startActivity(Intent(this, RidingActivity::class.java))
        }

        binding.cardRideHistory.setOnClickListener {
            startActivity(Intent(this, RideHistoryActivity::class.java))
        }

        binding.cardPermissionWarning.setOnClickListener {
            requestRidePermissions()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnThemeToggle.setOnClickListener {
            val newMode = when (prefs.themeMode) {
                ThemeMode.DARK -> ThemeMode.LIGHT
                else           -> ThemeMode.DARK
            }
            prefs.themeMode = newMode
            AppCompatDelegate.setDefaultNightMode(
                if (newMode == ThemeMode.DARK) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun isDarkTheme(): Boolean =
        prefs.themeMode == ThemeMode.DARK ||
            (prefs.themeMode == ThemeMode.SYSTEM &&
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)

    private fun updateThemeToggleIcon() {
        val dark = isDarkTheme()
        binding.btnThemeToggle.setImageResource(if (dark) R.drawable.ic_sun else R.drawable.ic_moon)
        binding.btnThemeToggle.contentDescription =
            getString(if (dark) R.string.switch_to_light else R.string.switch_to_dark)
    }

    private fun formatTimer(seconds: Int): String =
        if (seconds < 60) "${seconds}s"
        else "${seconds / 60} min" + if (seconds % 60 != 0) " ${seconds % 60}s" else ""

    /** Asks for the runtime permissions, routing to Settings when they are locked out. */
    private fun requestRidePermissions(thenStartRide: Boolean = false) {
        startRideAfterGrant = thenStartRide
        val blocked = PermissionHelper.missingForRide(this).isNotEmpty() &&
            PermissionHelper.startupPermissions().none { shouldShowRequestPermissionRationale(it) }

        if (blocked) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permissions_title)
                .setMessage(R.string.permissions_blocked_message)
                .setPositiveButton(R.string.open_settings) { _, _ -> PermissionHelper.openAppSettings(this) }
                .setNegativeButton(R.string.cancel) { _, _ -> startRideAfterGrant = false }
                .show()
        } else {
            permissionLauncher.launch(PermissionHelper.startupPermissions())
        }
    }

    private fun startRide(withDestination: Boolean = pendingWithDestination) {
        pendingWithDestination = withDestination
        // Android 14+ refuses to start the location foreground service without this.
        if (!PermissionHelper.hasLocation(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permissions_title)
                .setMessage(R.string.location_required_message)
                .setPositiveButton(R.string.btn_allow) { _, _ -> requestRidePermissions(thenStartRide = true) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val contacts = prefs.getEmergencyContacts()
        if (contacts.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_contact_title)
                .setMessage(R.string.no_contact_message)
                .setPositiveButton(R.string.add_contact) { _, _ ->
                    startActivity(Intent(this, EmergencyContactsActivity::class.java))
                }
                .setNegativeButton(R.string.ride_anyway) { _, _ -> confirmSmsThenRide() }
                .show()
        } else {
            confirmSmsThenRide()
        }
    }

    /**
     * SMS is the only SOS channel that works without data, so ask for it once,
     * at the point of need, with the reason spelled out. Declining is fine —
     * the SOS then falls back to opening the messaging app.
     */
    private fun confirmSmsThenRide() {
        if (PermissionHelper.hasSendSms(this) || prefs.smsPromptShown) {
            doStartRide()
            return
        }
        prefs.smsPromptShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.sms_permission_title)
            .setMessage(R.string.sms_permission_message)
            .setPositiveButton(R.string.btn_allow) { _, _ ->
                smsPermissionLauncher.launch(PermissionHelper.smsPermission())
            }
            .setNegativeButton(R.string.sms_permission_skip) { _, _ -> doStartRide() }
            .setCancelable(false)
            .show()
    }

    private fun doStartRide() {
        val serviceIntent = Intent(this, RideMonitoringService::class.java).apply {
            action = RideMonitoringService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            prefs.isRidingOnline = true
            startActivity(
                Intent(this, RidingActivity::class.java)
                    .putExtra(RidingActivity.EXTRA_ASK_DESTINATION, pendingWithDestination)
            )
        } catch (e: Exception) {
            prefs.isRidingOnline = false
            AlertDialog.Builder(this)
                .setTitle(R.string.ride_start_failed_title)
                .setMessage(getString(R.string.ride_start_failed_message, e.message ?: "unknown"))
                .setPositiveButton(R.string.open_settings) { _, _ -> PermissionHelper.openAppSettings(this) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
