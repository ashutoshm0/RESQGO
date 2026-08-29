package com.example.resqgo.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivitySettingsBinding
import com.example.resqgo.sos.HttpSmsSender
import com.example.resqgo.ui.contacts.EmergencyContactsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = UserPreferences(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
    }

    private fun setupUI() {
        binding.etRiderName.setText(prefs.riderName ?: "")
        binding.etFast2smsKey.setText(prefs.fast2smsApiKey)

        when (prefs.sensitivity) {
            "LOW"  -> binding.rgSensitivity.check(R.id.rbLow)
            "HIGH" -> binding.rgSensitivity.check(R.id.rbHigh)
            else   -> binding.rgSensitivity.check(R.id.rbMedium)
        }

        updatePermissionsStatus()
    }

    private fun updatePermissionsStatus() {
        val hasLoc   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)          == PackageManager.PERMISSION_GRANTED
        val hasSms   = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)            == PackageManager.PERMISSION_GRANTED

        binding.tvPermLocation.text = if (hasLoc)   "Location: ✅" else "Location: ❌"
        binding.tvPermPhone.text    = if (hasPhone)  "Phone: ✅"    else "Phone: ❌"

        if (hasSms) {
            binding.tvPermSms.text = "✅ Granted"
            binding.tvPermSms.setTextColor(Color.parseColor("#00E676"))
            binding.btnGrantSms.visibility = View.GONE
            binding.tvSmsHint.text = "SIM SMS active — all layers working"
        } else {
            binding.tvPermSms.text = "Not granted"
            binding.tvPermSms.setTextColor(Color.parseColor("#FFD600"))
            binding.btnGrantSms.visibility = View.VISIBLE
            binding.tvSmsHint.text = "Optional — tap Grant to enable SIM SMS"
        }
    }

    private fun setupListeners() {
        binding.btnManageContacts.setOnClickListener {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        }

        // Fast2SMS help dialog
        binding.btnFast2smsHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Fast2SMS — Auto SMS Setup")
                .setMessage(HttpSmsSender.SETUP_GUIDE)
                .setPositiveButton("Got it", null)
                .setNeutralButton("Open Website") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fast2sms.com")))
                }
                .show()
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
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Test SMS button ───────────────────────────────────────────────────
        binding.btnTestSms.setOnClickListener {
            val apiKey  = binding.etFast2smsKey.text.toString().trim()
            val contact = prefs.getPrimaryContact()

            if (apiKey.isBlank()) {
                binding.tvSmsTestResult.text = "Enter API key first"
                binding.tvSmsTestResult.setTextColor(Color.parseColor("#FF1744"))
                return@setOnClickListener
            }
            if (contact == null) {
                binding.tvSmsTestResult.text = "Add emergency contact first"
                binding.tvSmsTestResult.setTextColor(Color.parseColor("#FF1744"))
                return@setOnClickListener
            }

            binding.tvSmsTestResult.text = "Sending test SMS..."
            binding.tvSmsTestResult.setTextColor(Color.parseColor("#8899AA"))
            binding.btnTestSms.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                val ok = HttpSmsSender.sendTest(apiKey, contact.phone)
                withContext(Dispatchers.Main) {
                    binding.btnTestSms.isEnabled = true
                    if (ok) {
                        binding.tvSmsTestResult.text = "✅ Test SMS sent to ${contact.name}"
                        binding.tvSmsTestResult.setTextColor(Color.parseColor("#00E676"))
                        Toast.makeText(this@SettingsActivity,
                            "Test SMS sent! Check ${contact.name}'s phone.",
                            Toast.LENGTH_LONG).show()
                    } else {
                        binding.tvSmsTestResult.text = "❌ Failed — check API key or internet"
                        binding.tvSmsTestResult.setTextColor(Color.parseColor("#FF1744"))
                        Toast.makeText(this@SettingsActivity,
                            "Test failed. Check logcat for details.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // ── Save ──────────────────────────────────────────────────────────────
        binding.btnSave.setOnClickListener {
            prefs.riderName = binding.etRiderName.text.toString().trim()

            // Save Fast2SMS key
            val apiKey = binding.etFast2smsKey.text.toString().trim()
            if (apiKey.isNotBlank()) prefs.fast2smsApiKey = apiKey  // don't overwrite bundled key with empty

            val selectedSensitivity = when (binding.rgSensitivity.checkedRadioButtonId) {
                R.id.rbLow  -> "LOW"
                R.id.rbHigh -> "HIGH"
                else        -> "MEDIUM"
            }
            prefs.sensitivity = selectedSensitivity

            val msg = if (prefs.fast2smsApiKey.isNotBlank())
                "Settings saved — Fast2SMS active"
            else
                "Settings saved (No Fast2SMS key — using Android SMS)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
