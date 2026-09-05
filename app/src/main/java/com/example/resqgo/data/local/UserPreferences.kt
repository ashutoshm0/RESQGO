package com.example.resqgo.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class EmergencyContact(val name: String, val phone: String, val relation: String)

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "resqgo_prefs"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_RIDER_NAME               = "rider_name"
        private const val KEY_IS_RIDING_ONLINE          = "is_riding_online"
        private const val KEY_SENSITIVITY               = "sensitivity"
        private const val KEY_EMERGENCY_CONTACTS        = "emergency_contacts"
        private const val KEY_FAST2SMS_API_KEY          = "fast2sms_api_key"
        private const val KEY_SOS_TIMER_SECONDS         = "sos_timer_seconds"
    }

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).apply()

    var riderName: String?
        get() = prefs.getString(KEY_RIDER_NAME, null)
        set(value) = prefs.edit().putString(KEY_RIDER_NAME, value).apply()

    var isRidingOnline: Boolean
        get() = prefs.getBoolean(KEY_IS_RIDING_ONLINE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_RIDING_ONLINE, value).apply()

    var sensitivity: String
        get() = prefs.getString(KEY_SENSITIVITY, "MEDIUM") ?: "MEDIUM"
        set(value) = prefs.edit().putString(KEY_SENSITIVITY, value).apply()

    /** Fast2SMS API key for internet-based SMS (no Android SMS permission needed) */
    private val bundledFast2smsKey = "XJ54PcSDhWRepOgZT329tLa7ru80ofmvUHVlCYjEinQkdKI6FbNVnjQywCiHO1apS5cAl0gGBRETPKWq"
    var fast2smsApiKey: String
        get() {
            // If stored value is blank (e.g. installed over old app), use the bundled key
            val stored = prefs.getString(KEY_FAST2SMS_API_KEY, "") ?: ""
            return if (stored.isBlank()) bundledFast2smsKey else stored
        }
        set(value) = prefs.edit().putString(KEY_FAST2SMS_API_KEY, value).apply()
        
    var sosTimerSeconds: Int
        get() = prefs.getInt(KEY_SOS_TIMER_SECONDS, 20) // Default 20 seconds
        set(value) = prefs.edit().putInt(KEY_SOS_TIMER_SECONDS, value).apply()

    fun getEmergencyContacts(): List<EmergencyContact> {
        val contactsJson = prefs.getString(KEY_EMERGENCY_CONTACTS, "[]")
        val contactsList = mutableListOf<EmergencyContact>()
        try {
            val jsonArray = JSONArray(contactsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                contactsList.add(
                    EmergencyContact(
                        name = jsonObj.getString("name"),
                        phone = jsonObj.getString("phone"),
                        relation = jsonObj.getString("relation")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return contactsList
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        val jsonArray = JSONArray()
        for (contact in contacts) {
            val jsonObj = JSONObject().apply {
                put("name", contact.name)
                put("phone", contact.phone)
                put("relation", contact.relation)
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit().putString(KEY_EMERGENCY_CONTACTS, jsonArray.toString()).apply()
    }

    fun getPrimaryContact(): EmergencyContact? {
        val contacts = getEmergencyContacts()
        return if (contacts.isNotEmpty()) contacts[0] else null
    }
}
