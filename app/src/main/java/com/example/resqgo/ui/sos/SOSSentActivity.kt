package com.example.resqgo.ui.sos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.databinding.ActivitySosSentBinding
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import com.example.resqgo.ui.home.HomeActivity

class SOSSentActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosSentBinding
    private lateinit var prefs: UserPreferences
    private lateinit var sosManager: SOSManager

    private var latitude      = 0.0
    private var longitude     = 0.0
    private var address       = ""
    private var smsFailed     = false
    private var contactPhones = emptyArray<String>()
    private var contactNames  = emptyArray<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding    = ActivitySosSentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs      = UserPreferences(this)
        sosManager = SOSManager(this)
        RideMonitoringService.resetConfirmationFlag()

        latitude      = intent.getDoubleExtra("latitude",          0.0)
        longitude     = intent.getDoubleExtra("longitude",         0.0)
        address       = intent.getStringExtra("address")           ?: ""
        val notified  = intent.getIntExtra("contactsNotified",     0)
        val manually  = intent.getBooleanExtra("manuallyTriggered", false)
        smsFailed     = intent.getBooleanExtra("smsFailed",         false)
        contactPhones = intent.getStringArrayExtra("contactPhones") ?: emptyArray()
        contactNames  = intent.getStringArrayExtra("contactNames")  ?: emptyArray()

        setupUI(notified, manually)
        setupListeners()
        buildWhatsAppButtons()
    }

    // ── UI Setup ──────────────────────────────────────────
    private fun setupUI(contactsNotified: Int, manuallyTriggered: Boolean) {
        binding.tvSosTitle.text = if (manuallyTriggered) "🚨 Manual SOS Sent" else "🚨 SOS Alert Sent"

        // Contacts notification status
        if (smsFailed || contactsNotified == 0) {
            binding.tvContactsNotified.text =
                "⚠️ SMS blocked — use WhatsApp buttons below to alert contacts"
            binding.tvContactsNotified.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_warning)
            )
        } else {
            binding.tvContactsNotified.text =
                "✅ $contactsNotified contact(s) notified via SMS"
            binding.tvContactsNotified.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_success)
            )
        }

        // Address display
        val hasLocation = latitude != 0.0 || longitude != 0.0
        if (hasLocation) {
            binding.tvLocationSent.text =
                if (address.isNotBlank()) "📍 $address"
                else "📍 %.5f, %.5f".format(latitude, longitude)
            binding.tvMapLink.visibility = View.VISIBLE
        } else {
            binding.tvLocationSent.text  = "📍 Location unavailable"
            binding.tvMapLink.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        // Open in Google Maps
        binding.tvMapLink.setOnClickListener {
            if (latitude != 0.0 || longitude != 0.0) {
                val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(Accident Location)")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                    // Fallback: open in browser
                    val browserUri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
                    startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                }
            }
        }

        // Call primary contact
        binding.btnCallContact.setOnClickListener {
            val primary = prefs.getPrimaryContact()
            if (primary != null) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${primary.phone}")))
            }
        }

        // Find nearest hospital
        binding.btnNearestHospital.setOnClickListener {
            val searchUri = if (latitude != 0.0 || longitude != 0.0) {
                Uri.parse("geo:$latitude,$longitude?q=hospital+near+me")
            } else {
                Uri.parse("geo:0,0?q=hospital+near+me")
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, searchUri))
            } catch (_: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/hospital+near+me")))
            }
        }

        // I'm Safe → go home
        binding.btnImSafe.setOnClickListener { goHome() }
    }

    // ── WhatsApp buttons per contact ───────────────────────
    private fun buildWhatsAppButtons() {
        val container = binding.whatsappButtonsContainer
        container.removeAllViews()
        if (contactPhones.isEmpty()) return

        val riderName = prefs.riderName ?: "Rider"
        val message   = buildString {
            append("🚨 [RESQGO ALERT]\n")
            append("*$riderName* may have been in an accident!\n\n")
            if (address.isNotBlank()) append("📍 *Location:* $address\n")
            if (latitude != 0.0 || longitude != 0.0)
                append("🗺 *Maps:* https://maps.google.com/?q=$latitude,$longitude\n\n")
            append("Please call immediately or contact emergency services (112).")
        }

        val header = TextView(this).apply {
            text     = "📱 Send via WhatsApp"
            textSize = 13f
            setPadding(0, 24, 0, 8)
            setTextColor(ContextCompat.getColor(this@SOSSentActivity, R.color.resqgo_text_secondary))
        }
        container.addView(header)

        contactPhones.forEachIndexed { index, phone ->
            val name   = contactNames.getOrNull(index) ?: "Contact ${index + 1}"
            val button = Button(this).apply {
                text = "🟢 WhatsApp → $name"
                setBackgroundColor(ContextCompat.getColor(this@SOSSentActivity, R.color.resqgo_success))
                setTextColor(ContextCompat.getColor(this@SOSSentActivity, R.color.white))
                textSize     = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 144
                ).also { lp -> lp.topMargin = 12 }
                setOnClickListener { sosManager.openWhatsApp(phone, message) }
            }
            container.addView(button)
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }
}
