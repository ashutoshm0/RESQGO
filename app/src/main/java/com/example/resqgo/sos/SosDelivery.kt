package com.example.resqgo.sos

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** The routes an SOS can go out through, in escalation order. */
enum class SosChannel(val label: String) {
    SIM_SMS("SMS via SIM"),
    INTERNET_SMS("SMS via internet"),
    PHONE_CALL("Emergency call"),
    WHATSAPP("WhatsApp")
}

/** The real, observed result of one delivery attempt. Never assumed. */
data class SosOutcome(
    val channel: SosChannel,
    val target: String,
    val ok: Boolean,
    val detail: String
)

/**
 * Emergency delivery with genuine confirmation.
 *
 * The previous implementation called `sendMultipartTextMessage(..., null, null)` and
 * reported success whenever no exception was thrown — so a message that never left
 * the handset (no credit, no service, radio off) still showed "SOS Alert Sent".
 * The spec calls that out explicitly: silent failure is unacceptable for SOS.
 * Every send here carries a sent-PendingIntent and waits for the platform's verdict.
 */
object SosDelivery {

    private const val TAG = "SosDelivery"

    /** Broadcast so the SOS screen can render live per-channel status. */
    const val ACTION_STATUS = "com.example.resqgo.SOS_STATUS"
    const val EXTRA_CHANNEL = "channel"
    const val EXTRA_TARGET = "target"
    const val EXTRA_OK = "ok"
    const val EXTRA_DETAIL = "detail"

    fun broadcast(context: Context, outcome: SosOutcome) {
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CHANNEL, outcome.channel.name)
            putExtra(EXTRA_TARGET, outcome.target)
            putExtra(EXTRA_OK, outcome.ok)
            putExtra(EXTRA_DETAIL, outcome.detail)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "${outcome.channel} -> ${outcome.target}: ok=${outcome.ok} (${outcome.detail})")
    }

    private fun errorText(code: Int): String = when (code) {
        Activity.RESULT_OK -> "delivered to network"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure — check balance/SIM"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no network service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off / airplane mode"
        else -> "failed (code $code)"
    }

    /**
     * Sends one SMS through the handset SIM and waits for the platform to confirm
     * it actually reached the network. Returns a real outcome, never an assumption.
     * Iterates through available SIMs on dual-SIM devices to ensure delivery.
     */
    suspend fun sendSimSms(
        context: Context,
        phone: String,
        message: String,
        timeoutMs: Long = 25_000L
    ): SosOutcome {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SosOutcome(SosChannel.SIM_SMS, phone, false, "SMS permission not granted")
        }

        val smsManagers = mutableListOf<SmsManager>()
        try {
            val subscriptionManager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            if (subscriptionManager != null && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val activeSubs = subscriptionManager.activeSubscriptionInfoList
                if (activeSubs != null && activeSubs.isNotEmpty()) {
                    for (sub in activeSubs) {
                        smsManagers.add(SmsManager.getSmsManagerForSubscriptionId(sub.subscriptionId))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get subscription info: ${e.message}")
        }

        if (smsManagers.isEmpty()) {
            try {
                val defaultSm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION") SmsManager.getDefault()
                }
                if (defaultSm != null) smsManagers.add(defaultSm)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get default SmsManager: ${e.message}")
            }
        }

        if (smsManagers.isEmpty()) {
            return SosOutcome(SosChannel.SIM_SMS, phone, false, "no SMS service on device")
        }

        var lastOutcome: SosOutcome? = null
        // Allocate time fairly among available SIMs
        val perSimTimeout = timeoutMs / smsManagers.size

        for (smsManager in smsManagers) {
            val outcome = attemptSendWithManager(context, smsManager, phone, message, perSimTimeout)
            lastOutcome = outcome
            if (outcome.ok) {
                return outcome // Success on this SIM!
            }
            Log.d(TAG, "SMS failed on one SIM (${outcome.detail}), trying next if available...")
        }

        return lastOutcome ?: SosOutcome(SosChannel.SIM_SMS, phone, false, "unknown failure")
    }

    private suspend fun attemptSendWithManager(
        context: Context,
        smsManager: SmsManager,
        phone: String,
        message: String,
        timeoutMs: Long
    ): SosOutcome {
        val action = "com.example.resqgo.SMS_SENT.${System.nanoTime()}"

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<SosOutcome> { cont ->
                val parts = try {
                    smsManager.divideMessage(message)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(SosOutcome(SosChannel.SIM_SMS, phone, false, "divideMessage threw: ${e.javaClass.simpleName}"))
                    return@suspendCancellableCoroutine
                }
                
                var reported = 0
                var firstFailure: Int? = null

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        reported++
                        if (resultCode != Activity.RESULT_OK && firstFailure == null) {
                            firstFailure = resultCode
                        }
                        // Wait for every part before declaring success.
                        if (reported >= parts.size || firstFailure != null) {
                            runCatching { context.unregisterReceiver(this) }
                            if (cont.isActive) {
                                val code = firstFailure
                                cont.resume(
                                    SosOutcome(
                                        SosChannel.SIM_SMS, phone,
                                        code == null,
                                        errorText(code ?: Activity.RESULT_OK)
                                    )
                                )
                            }
                        }
                    }
                }

                val filter = IntentFilter(action)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(receiver, filter)
                }
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

                val sentIntents = ArrayList<PendingIntent>(parts.size)
                for (i in parts.indices) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            context, i,
                            Intent(action).setPackage(context.packageName),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }

                try {
                    smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
                } catch (e: Exception) {
                    runCatching { context.unregisterReceiver(receiver) }
                    if (cont.isActive) {
                        cont.resume(
                            SosOutcome(
                                SosChannel.SIM_SMS, phone, false,
                                "send threw: ${e.javaClass.simpleName}"
                            )
                        )
                    }
                }
            }
        }

        return result
            ?: SosOutcome(SosChannel.SIM_SMS, phone, false, "no confirmation within ${timeoutMs / 1000}s")
    }
}
