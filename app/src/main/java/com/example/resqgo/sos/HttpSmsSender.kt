package com.example.resqgo.sos

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sends SMS via Fast2SMS HTTP API — works WITHOUT Android SEND_SMS permission.
 * Requires: Free API key from https://fast2sms.com (sign up with Indian phone number)
 *
 * Free tier: 15 credits on signup (~30 SMS). Top-up: ₹100 = 500+ SMS.
 *
 * NOTE: Requires INTERNET permission (already granted in manifest).
 */
object HttpSmsSender {

    private const val TAG      = "HttpSmsSender"
    private const val FAST2SMS = "https://www.fast2sms.com/dev/bulkV2"

    /**
     * Strip emoji and non-ASCII characters so Fast2SMS route=q accepts the message.
     * The Quick route only supports plain English text.
     */
    private fun stripEmoji(text: String): String {
        // Remove emoji and any character outside printable ASCII (32–126)
        return text
            .replace(Regex("[^\\x20-\\x7E\\n]"), "")  // keep only printable ASCII + newlines
            .replace("*", "")                           // markdown bold markers not needed in SMS
            .trim()
    }

    /**
     * Send SOS SMS via Fast2SMS API.
     * @param apiKey   Your Fast2SMS API key from dashboard
     * @param phones   List of 10-digit Indian phone numbers (no +91)
     * @param message  Message to send (emoji will be stripped automatically)
     * @return true if API accepted the request
     */
    fun sendVieFast2SMS(apiKey: String, phones: List<String>, message: String): Boolean {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Fast2SMS API key not configured — SMS not sent")
            return false
        }
        if (phones.isEmpty()) {
            Log.w(TAG, "No phone numbers provided — SMS not sent")
            return false
        }

        val cleanMessage = stripEmoji(message)
        Log.d(TAG, "Sending SMS via Fast2SMS | numbers=${phones.size} | msg_len=${cleanMessage.length}")

        return try {
            // Fast2SMS expects plain 10-digit numbers, no country code
            val numbers = phones.joinToString(",") {
                val digits = it.filter { c -> c.isDigit() }
                // Strip leading 91 if present, take last 10 digits
                if (digits.length == 12 && digits.startsWith("91")) digits.substring(2)
                else digits.takeLast(10)
            }

            val body = buildString {
                append("sender_id=").append(URLEncoder.encode("FSTSMS", "UTF-8"))
                append("&message=").append(URLEncoder.encode(cleanMessage, "UTF-8"))
                append("&language=english")
                append("&route=q")          // Quick route — no DLT needed
                append("&numbers=").append(URLEncoder.encode(numbers, "UTF-8"))
            }

            Log.d(TAG, "POST to $FAST2SMS | numbers=$numbers")

            val url  = URL(FAST2SMS)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod  = "POST"
                doOutput       = true
                connectTimeout = 10_000
                readTimeout    = 10_000
                setRequestProperty("authorization", apiKey)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

            val code = conn.responseCode

            // Read response — use errorStream for non-2xx codes
            val response = try {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText()
                    ?: "No error body (code=$code)"
            }
            conn.disconnect()

            Log.d(TAG, "Fast2SMS [$code] response: $response")

            if (code == 200 && response.contains("\"return\":true", ignoreCase = true)) {
                Log.d(TAG, "✅ Fast2SMS SUCCESS — SMS sent to: $numbers")
                true
            } else {
                Log.e(TAG, "❌ Fast2SMS FAILED [$code] — response: $response")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fast2SMS network error", e)
            false
        }
    }

    /**
     * Sends a single test SMS to verify the API key is working.
     * Used by the Settings "Test" button.
     */
    fun sendTest(apiKey: String, phone: String): Boolean {
        return sendVieFast2SMS(
            apiKey  = apiKey,
            phones  = listOf(phone),
            message = "RESQGO Test: Your Fast2SMS API key is working correctly. SOS alerts will be sent to this number automatically."
        )
    }

    val SETUP_GUIDE = """
HOW TO GET YOUR FREE API KEY:
1. Go to https://fast2sms.com
2. Sign up with your Indian mobile number
3. Verify OTP
4. Go to Dashboard -> Dev API
5. Copy the API key
6. Paste it in Settings -> AUTO SMS field
7. Tap TEST to verify it works

FREE CREDITS: 15 credits on signup (~30 SMS).
No SMS permission needed -- uses internet instead.
""".trimIndent()
}
