package com.example.resqgo.sos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.resqgo.data.local.EmergencyContact
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.data.repository.RideRepository
import com.example.resqgo.location.LocationTracker
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.ui.sos.SOSSentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SOSManager(private val context: Context) {

    private val prefs   = UserPreferences(context)
    private val handler = Handler(Looper.getMainLooper())

    private val repository: RideRepository
        get() = (context.applicationContext as com.example.resqgo.ResqgoApp).repository

    private val tracker: LocationTracker
        get() = RideMonitoringService.instance?.locationTracker ?: LocationTracker(context)

    companion object {
        const val TAG = "SOSManager"
    }

    // ── Main SOS trigger ──────────────────────────────────────────────────────
    fun triggerSOS(manuallyTriggered: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {

            // ✨ Step 1: Get location ✨
            val location  = tracker.getCurrentLocation() ?: tracker.getLastLocation()
            val latitude  = location?.latitude  ?: 0.0
            val longitude = location?.longitude ?: 0.0
            val hasLoc    = latitude != 0.0 || longitude != 0.0

            val address = if (hasLoc) tracker.getAddressFromLocation(latitude, longitude)
                          else        "Location unavailable"

            val contacts  = prefs.getEmergencyContacts()
            val riderName = prefs.riderName ?: "Rider"

            val plainMessage = buildPlainMessage(riderName, latitude, longitude, address)
            Log.d(TAG, "SOS triggered | contacts=${contacts.size} | location=$address")

            if (contacts.isEmpty()) {
                Log.w(TAG, "No emergency contacts configured!")
                withContext(Dispatchers.Main) {
                    showSOSSentScreen(latitude, longitude, address, 0, manuallyTriggered, true, contacts, plainMessage)
                }
                return@launch
            }

            // ✨ Step 2: Show SOS Sent screen IMMEDIATELY so app stays in foreground ✨
            // This prevents Android 10+ background activity launch restrictions.
            withContext(Dispatchers.Main) {
                showSOSSentScreen(
                    latitude          = latitude,
                    longitude         = longitude,
                    address           = address,
                    contactsNotified  = contacts.size,
                    manuallyTriggered = manuallyTriggered,
                    // Nothing has been confirmed yet. The screen starts in a
                    // "sending" state and is corrected by SosDelivery broadcasts.
                    smsFailed         = true,
                    contacts          = contacts,
                    sosMessage        = plainMessage
                )
            }

            // ✨ Step 3: Send messages via 3-layer fallback in background ✨
            // Every attempt below reports its REAL outcome; nothing is assumed sent.
            val phones = contacts.map { it.phone }

            // Layer 1: handset SIM, confirmed by a sent-PendingIntent receipt.
            var smsSent = false
            for (phone in phones) {
                val outcome = SosDelivery.sendSimSms(context, phone, plainMessage)
                SosDelivery.broadcast(context, outcome)
                if (outcome.ok) smsSent = true
            }

            // Layer 2: internet SMS, only if the SIM route delivered nothing.
            if (!smsSent) {
                val layer2ok = trySendViaInternet(phones, plainMessage)
                SosDelivery.broadcast(
                    context,
                    SosOutcome(
                        SosChannel.INTERNET_SMS,
                        phones.joinToString(", "),
                        layer2ok,
                        if (layer2ok) "accepted by gateway" else "gateway rejected or unreachable"
                    )
                )
                if (layer2ok) smsSent = true
            }

            Log.d(TAG, "SMS delivered to at least one contact: $smsSent")

            // ✨ Step 4: Call the PRIMARY contact ✨
            // This runs BEFORE bookkeeping: placing the call is the single most
            // important action after a crash and must not wait on anything else.
            // Calling every contact in sequence causes telecom crashes on some
            // Xiaomi/Samsung builds, so only the primary is dialled.
            if (contacts.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    makeCall(contacts[0].phone)
                }
            }

            // ✨ Step 5: Log the event (best effort, never blocking) ✨
            try {
                repository.logEvent(
                    latitude = latitude,
                    longitude = longitude,
                    eventType = if (manuallyTriggered) "SOS_MANUAL" else "SOS_AUTO",
                    address = address,
                    smsSent = smsSent,
                    contactsNotified = if (smsSent) contacts.size else 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log SOS event", e)
            }
        }
    }

    // ── LAYER 2: Internet SMS via Fast2SMS ────────────────────────────────────
    private fun trySendViaInternet(phones: List<String>, message: String): Boolean {
        val apiKey = prefs.fast2smsApiKey
        if (apiKey.isBlank()) {
            Log.w(TAG, "Layer 2 skipped: No Fast2SMS API key configured")
            return false
        }
        val ok = HttpSmsSender.sendVieFast2SMS(apiKey, phones, message)
        if (ok) Log.d(TAG, "✅ Layer 2 (Fast2SMS) sent to ${phones.size} contacts")
        else    Log.e(TAG, "❌ Layer 2 (Fast2SMS) failed")
        return ok
    }

    // ── Call contacts sequentially with 12s gap ───────────────────────────────
    private fun callAllContactsSequentially(contacts: List<EmergencyContact>, index: Int) {
        if (index >= contacts.size) return

        val contact = contacts[index]
        Log.d(TAG, "📞 Calling ${contact.name} (${contact.phone}) ...")
        makeCall(contact.phone)

        if (index + 1 < contacts.size) {
            handler.postDelayed({ callAllContactsSequentially(contacts, index + 1) }, 12_000L)
        }
    }

    // ── Phone call ────────────────────────────────────────────────────────────
    private fun makeCall(phoneNumber: String): Boolean {
        val sanitized = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = Uri.parse("tel:$sanitized")
        val autoDial = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED

        // ACTION_CALL places the call outright; without CALL_PHONE we can only
        // open the dialer pre-filled, which still needs one tap from the rider.
        val intent = Intent(if (autoDial) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            SosDelivery.broadcast(
                context,
                SosOutcome(
                    SosChannel.PHONE_CALL, phoneNumber, true,
                    if (autoDial) "call placed automatically" else "dialer opened — tap call"
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Call failed to $phoneNumber", e)
            SosDelivery.broadcast(
                context,
                SosOutcome(SosChannel.PHONE_CALL, phoneNumber, false, "no app could place the call")
            )
            false
        }
    }

    // ── WhatsApp opener (manual fallback shown on SOSSentActivity) ────────────
    fun openWhatsApp(phoneNumber: String, message: String): Boolean {
        val formatted = formatPhone(phoneNumber)
        return try {
            val uri    = Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            SosDelivery.broadcast(
                context,
                SosOutcome(SosChannel.WHATSAPP, phoneNumber, true, "WhatsApp opened with the alert")
            )
            true
        } catch (e: Exception) {
            try {
                // Fallback: wa.me through the browser when the app is not installed.
                val uri    = Uri.parse("https://wa.me/$formatted?text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
                SosDelivery.broadcast(
                    context,
                    SosOutcome(SosChannel.WHATSAPP, phoneNumber, true, "opened via wa.me link")
                )
                true
            } catch (e2: Exception) {
                Log.e(TAG, "WhatsApp unavailable", e2)
                SosDelivery.broadcast(
                    context,
                    SosOutcome(SosChannel.WHATSAPP, phoneNumber, false, "WhatsApp not installed")
                )
                false
            }
        }
    }

    // ── LAYER 3: Intent SMS (opens SMS app — no permission needed) ────────────
    // Used as manual fallback from SOSSentActivity when Layers 1 & 2 fail.
    // User must tap "Send" in the messaging app.
    fun openSmsIntent(phoneNumber: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data  = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "SMS intent failed", e)
        }
    }

    fun formatPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            phone.startsWith("+")                        -> digits
            digits.length == 10                          -> "91$digits"
            digits.startsWith("91") && digits.length == 12 -> digits
            else                                         -> digits
        }
    }

    // ── Message builders ───────────────────────────────────────────────────────

    /** Full message with emoji for WhatsApp */
    fun buildFullMessage(riderName: String, lat: Double, lng: Double, address: String): String {
        val mapsUrl = "https://maps.google.com/?q=$lat,$lng"
        return "🚨 RESQGO EMERGENCY ALERT\n\n" +
               "$riderName may have been in an accident!\n\n" +
               "📍 Location: $address\n" +
               "🗺 Maps: $mapsUrl\n\n" +
               "Please call immediately or dial 112."
    }

    /** Plain ASCII message for SMS (no emoji — required for SIM/Fast2SMS) */
    fun buildPlainMessage(riderName: String, lat: Double, lng: Double, address: String): String {
        val mapsUrl = "https://maps.google.com/?q=$lat,$lng"
        return "RESQGO EMERGENCY: $riderName may have been in an accident!\n" +
               "Location: $address\n" +
               "Maps: $mapsUrl\n" +
               "Call immediately or dial 112."
    }

    // ── Show SOS Sent screen ───────────────────────────────────────────────────
    private fun showSOSSentScreen(
        latitude: Double, longitude: Double,
        address: String,
        contactsNotified: Int,
        manuallyTriggered: Boolean,
        smsFailed: Boolean,
        contacts: List<EmergencyContact>,
        sosMessage: String
    ) {
        val intent = Intent(context, SOSSentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("latitude",          latitude)
            putExtra("longitude",         longitude)
            putExtra("address",           address)
            putExtra("contactsNotified",  contactsNotified)
            putExtra("manuallyTriggered", manuallyTriggered)
            putExtra("smsFailed",         smsFailed)
            putExtra("sosMessage",        sosMessage)
            putExtra("contactPhones",     contacts.map { it.phone }.toTypedArray())
            putExtra("contactNames",      contacts.map { it.name  }.toTypedArray())
        }
        context.startActivity(intent)
    }
}
