package com.example.resqgo.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivityHomeBinding
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import com.example.resqgo.ui.contacts.EmergencyContactsActivity
import com.example.resqgo.ui.history.RideHistoryActivity
import com.example.resqgo.ui.riding.RidingActivity
import com.example.resqgo.ui.settings.SettingsActivity
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: UserPreferences
    private lateinit var sosManager: SOSManager

    companion object {
        const val ACTION_SENSOR_UPDATE = "com.example.resqgo.SENSOR_UPDATE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPreferences(this)
        sosManager = SOSManager(this)

        setupListeners()
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
        binding.tvRiderName.text = if (!name.isNullOrBlank()) name else getString(R.string.app_name)
    }

    private fun updateContactsCard() {
        val contacts = prefs.getEmergencyContacts()
        if (contacts.isEmpty()) {
            binding.tvContactsSummary.text = getString(R.string.no_contacts)
            binding.tvContactsHint.text = "Tap Manage to add emergency contacts"
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
            binding.btnStartRide.text = "RETURN TO RIDE"
        } else {
            binding.tvSafetyStatus.text = getString(R.string.status_ready)
            binding.tvSafetyStatus.setTextColor(ContextCompat.getColor(this, R.color.resqgo_text_secondary))
            binding.cardActiveBanner.visibility = View.GONE
            binding.btnStartRide.text = getString(R.string.btn_start_ride)
        }
    }

    private fun setupListeners() {
        binding.btnStartRide.setOnClickListener {
            if (prefs.isRidingOnline) {
                // Already riding — go back to riding screen
                startActivity(Intent(this, RidingActivity::class.java))
            } else {
                startRide()
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

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun startRide() {
        val contacts = prefs.getEmergencyContacts()
        if (contacts.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Emergency Contact")
                .setMessage("Please add an emergency contact before starting your ride. This ensures someone is notified if you have an accident.")
                .setPositiveButton("Add Contact") { _, _ ->
                    startActivity(Intent(this, EmergencyContactsActivity::class.java))
                }
                .setNegativeButton("Ride Anyway") { _, _ ->
                    doStartRide()
                }
                .show()
        } else {
            doStartRide()
        }
    }

    private fun doStartRide() {
        val serviceIntent = Intent(this, RideMonitoringService::class.java).apply {
            action = RideMonitoringService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        prefs.isRidingOnline = true
        startActivity(Intent(this, RidingActivity::class.java))
    }
}
