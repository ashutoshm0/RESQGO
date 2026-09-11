package com.example.resqgo.ui.sos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.example.resqgo.util.applySystemBarPadding
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
import com.example.resqgo.sos.SosChannel
import com.example.resqgo.sos.SosDelivery
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
    private var sosMessage    = ""
    private var anySmsDelivered = false

    /** Renders each channel's real outcome as SOSManager reports it. */
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != SosDelivery.ACTION_STATUS) return
            val channel = runCatching {
                SosChannel.valueOf(i.getStringExtra(SosDelivery.EXTRA_CHANNEL) ?: "")
            }.getOrNull() ?: return
            val ok = i.getBooleanExtra(SosDelivery.EXTRA_OK, false)
            val target = i.getStringExtra(SosDelivery.EXTRA_TARGET) ?: ""
            val detail = i.getStringExtra(SosDelivery.EXTRA_DETAIL) ?: ""
            addStatusRow(channel, target, ok, detail)

            if (ok && (channel == SosChannel.SIM_SMS || channel == SosChannel.INTERNET_SMS)) {
                anySmsDelivered = true
            }
            refreshHeadline()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding    = ActivitySosSentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)
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
        sosMessage    = intent.getStringExtra("sosMessage")          ?: ""

        setupUI(notified, manually)
        setupListeners()
        buildWhatsAppButtons()
    }

    // ── UI Setup ──────────────────────────────────────────
    private fun setupUI(contactsNotified: Int, manuallyTriggered: Boolean) {
        binding.tvSosTitle.text = if (manuallyTriggered) "🚨 Manual SOS Sent" else "🚨 SOS Alert Sent"

        // Start honest: nothing is confirmed until a channel reports back.
        if (contactsNotified == 0) {
            binding.tvContactsNotified.text = "⚠️ No emergency contacts saved"
            binding.tvContactsNotified.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_alert)
            )
        } else {
            binding.tvContactsNotified.text = "Contacting $contactsNotified contact(s)…"
            binding.tvContactsNotified.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_warning)
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
                val sanitized = primary.phone.replace(Regex("[^0-9+]"), "")
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")))
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

        // Layer 3 fallback: opens the messaging app pre-filled. Needs no
        // permission, but the rider has to press Send.
        binding.btnSmsFallback.setOnClickListener {
            val primary = contactPhones.firstOrNull() ?: return@setOnClickListener
            sosManager.openSmsIntent(primary, sosMessage)
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

    /** One row per delivery attempt: what was tried, where, and what happened. */
    private fun addStatusRow(channel: SosChannel, target: String, ok: Boolean, detail: String) {
        val row = TextView(this).apply {
            val mark = if (ok) "✅" else "❌"
            text = "$mark  ${channel.label} → $target\n     $detail"
            textSize = 13f
            setPadding(0, 6, 0, 6)
            setTextColor(
                ContextCompat.getColor(
                    this@SOSSentActivity,
                    if (ok) R.color.resqgo_success else R.color.resqgo_alert
                )
            )
        }
        binding.deliveryStatusContainer.addView(row)

        // Offer the no-permission SMS fallback the moment messaging fails.
        if (!ok && (channel == SosChannel.SIM_SMS || channel == SosChannel.INTERNET_SMS)) {
            binding.btnSmsFallback.visibility = View.VISIBLE
        }
    }

    private fun refreshHeadline() {
        if (anySmsDelivered) {
            binding.tvContactsNotified.text = "✅ Alert delivered to your emergency contact(s)"
            binding.tvContactsNotified.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_success)
            )
            binding.btnSmsFallback.visibility = View.GONE
        } else {
            binding.tvContactsNotified.text =
                "⚠️ SMS not confirmed — use the buttons below to reach your contacts"
            binding.tvContactsNotified.setTextColor(
                ContextCompat.getColor(this, R.color.resqgo_alert)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SosDelivery.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(statusReceiver) }
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
