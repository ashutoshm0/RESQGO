package com.example.resqgo.ui.settings

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import com.example.resqgo.util.applySystemBarPadding
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.resqgo.R
import com.example.resqgo.ResqgoApp
import com.example.resqgo.data.local.ThemeMode
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivitySettingsBinding
import com.example.resqgo.ui.contacts.EmergencyContactsActivity
import com.example.resqgo.util.PermissionHelper
import com.google.android.material.chip.Chip

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: UserPreferences

    /** Chip id → duration in seconds, in ascending order. */
    private val timerOptions by lazy {
        listOf(
            R.id.chip30s to 30,
            R.id.chip40s to 40,
            R.id.chip1m to 60,
            R.id.chip2m to 120,
            R.id.chip3m to 180,
            R.id.chip4m to 240,
            R.id.chip5m to 300
        )
    }

    /** Guards the chip listener while we set the checked state programmatically. */
    private var suppressTimerCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        prefs = UserPreferences(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
        updateDiagnosticsCard()
    }

    private fun setupUI() {
        binding.etRiderName.setText(prefs.riderName ?: "")

        when (prefs.sensitivity) {
            "LOW"  -> binding.rgSensitivity.check(R.id.rbLow)
            "HIGH" -> binding.rgSensitivity.check(R.id.rbHigh)
            else   -> binding.rgSensitivity.check(R.id.rbMedium)
        }

        selectTimer(prefs.sosTimerSeconds, persist = false)

        updatePermissionsStatus()
        updateDiagnosticsCard()
        binding.switchDarkMode.isChecked = prefs.themeMode == ThemeMode.DARK
    }

    // ── Crash confirmation timer ─────────────────────────────────────────────

    /**
     * Applies [seconds] to the chips, the big read-out and the summary line, and
     * (unless [persist] is false) saves it immediately. Saving on selection rather
     * than only on "Save Settings" is what makes this control feel reliable.
     */
    private fun selectTimer(seconds: Int, persist: Boolean = true) {
        val index = timerOptions.indexOfFirst { it.second == seconds }
            .takeIf { it >= 0 } ?: 0
        val (chipId, value) = timerOptions[index]

        suppressTimerCallback = true
        binding.chipGroupTimer.check(chipId)
        suppressTimerCallback = false

        binding.tvTimerValue.text = formatTimer(value)
        binding.tvTimerSummary.text = getString(R.string.timer_selected_summary, formatTimer(value))
        binding.btnTimerDown.isEnabled = index > 0
        binding.btnTimerUp.isEnabled = index < timerOptions.lastIndex

        if (persist && prefs.sosTimerSeconds != value) {
            prefs.sosTimerSeconds = value
        }
    }

    private fun stepTimer(delta: Int) {
        val current = timerOptions.indexOfFirst { it.second == prefs.sosTimerSeconds }
            .takeIf { it >= 0 } ?: 0
        val next = (current + delta).coerceIn(0, timerOptions.lastIndex)
        if (next != current) {
            selectTimer(timerOptions[next].second)
            Toast.makeText(
                this,
                getString(R.string.timer_saved, formatTimer(timerOptions[next].second)),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatTimer(seconds: Int): String = when {
        seconds < 60          -> "$seconds seconds"
        seconds == 60         -> "1 minute"
        seconds % 60 == 0     -> "${seconds / 60} minutes"
        else                  -> "${seconds / 60} min ${seconds % 60} s"
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun updatePermissionsStatus() {
        val hasLoc = PermissionHelper.hasLocation(this)
        val hasPhone = PermissionHelper.hasCallPhone(this)
        val hasSms = PermissionHelper.hasSendSms(this)

        binding.tvPermLocation.text = getString(if (hasLoc) R.string.perm_granted else R.string.perm_denied)
        binding.tvPermPhone.text = getString(if (hasPhone) R.string.perm_granted else R.string.perm_denied)

        if (hasSms) {
            binding.tvPermSms.text = getString(R.string.perm_granted)
            binding.tvPermSms.setTextColor(Color.parseColor("#2E7D32"))
            binding.btnGrantSms.visibility = View.GONE
            binding.tvSmsHint.text = "SIM SMS active — all layers working"
        } else {
            binding.tvPermSms.text = getString(R.string.perm_not_granted)
            binding.tvPermSms.setTextColor(Color.parseColor("#D2691E"))
            binding.btnGrantSms.visibility = View.VISIBLE
            binding.tvSmsHint.text = "Optional — tap Grant to enable SIM SMS"
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────────

    private fun updateDiagnosticsCard() {
        binding.cardDiagnostics.visibility =
            if (ResqgoApp.readLastCrash(this) != null) View.VISIBLE else View.GONE
    }

    private fun showLastCrash() {
        val trace = ResqgoApp.readLastCrash(this) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.diagnostics_title)
            .setMessage(trace.take(4000))
            .setPositiveButton(R.string.diagnostics_clear) { _, _ ->
                ResqgoApp.clearLastCrash(this)
                updateDiagnosticsCard()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    private fun setupListeners() {
        binding.btnManageContacts.setOnClickListener {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        }

        binding.chipGroupTimer.setOnCheckedStateChangeListener { group, checkedIds ->
            if (suppressTimerCallback) return@setOnCheckedStateChangeListener
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val seconds = timerOptions.firstOrNull { it.first == id }?.second
                ?: return@setOnCheckedStateChangeListener
            selectTimer(seconds)
            (group.findViewById<Chip>(id))?.let {
                Toast.makeText(
                    this,
                    getString(R.string.timer_saved, formatTimer(seconds)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnTimerDown.setOnClickListener { stepTimer(-1) }
        binding.btnTimerUp.setOnClickListener { stepTimer(+1) }

        binding.btnViewCrash.setOnClickListener { showLastCrash() }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) ThemeMode.DARK else ThemeMode.LIGHT
            prefs.themeMode = newMode
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Grant SMS — opens RESQGO's App Settings so user can manually allow SMS
        binding.btnGrantSms.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Enable SIM SMS")
                .setMessage(
                    "Android blocked automatic SMS permission for security.\n\n" +
                    "To enable your phone's SIM for SMS:\n" +
                    "1. Tap OPEN SETTINGS below\n" +
                    "2. Tap \"Permissions\"\n" +
                    "3. Tap \"SMS\"\n" +
                    "4. Select \"Allow\"\n" +
                    "5. Come back here — you'll see ✅ Granted\n\n" +
                    "This lets RESQGO send SOS SMS directly using your SIM — works offline!"
                )
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // ── Save ──────────────────────────────────────────────────────────────
        binding.btnSave.setOnClickListener {
            prefs.riderName = binding.etRiderName.text.toString().trim()

            prefs.sensitivity = when (binding.rgSensitivity.checkedRadioButtonId) {
                R.id.rbLow  -> "LOW"
                R.id.rbHigh -> "HIGH"
                else        -> "MEDIUM"
            }

            // The timer already persisted when it was picked; nothing to do here.
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
