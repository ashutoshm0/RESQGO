package com.example.resqgo.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class EmergencyContact(val name: String, val phone: String, val relation: String)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;
    companion object {
        fun fromString(value: String): ThemeMode {
            return try { valueOf(value) } catch (_: Exception) { SYSTEM }
        }
    }
}

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = try {
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME_SECURE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREF_NAME_FALLBACK, Context.MODE_PRIVATE)
        }

        // Migrate legacy unencrypted preferences if they exist
        val legacyPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (legacyPrefs.all.isNotEmpty()) {
            val editor = prefs.edit()
            for ((key, value) in legacyPrefs.all) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            editor.apply()
            legacyPrefs.edit().clear().apply()
        }
    }

    companion object {
        private const val PREF_NAME = "resqgo_prefs"
        private const val PREF_NAME_SECURE = "resqgo_secure_prefs"
        private const val PREF_NAME_FALLBACK = "resqgo_secure_prefs_fallback"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_RIDER_NAME               = "rider_name"
        private const val KEY_IS_RIDING_ONLINE          = "is_riding_online"
        private const val KEY_SENSITIVITY               = "sensitivity"
        private const val KEY_EMERGENCY_CONTACTS        = "emergency_contacts"
        private const val KEY_FAST2SMS_API_KEY          = "fast2sms_api_key"
private const val KEY_DARK_MODE_ENABLED         = "dark_mode_enabled"
        private const val KEY_THEME_MODE                = "theme_mode"
        private const val KEY_SOS_TIMER_SECONDS         = "sos_timer_seconds"
        private const val KEY_SMS_PROMPT_SHOWN          = "sms_prompt_shown"
        private const val KEY_SENSORS_AVAILABLE         = "sensors_available"
        private const val KEY_GPS_READY                 = "gps_ready"
        private const val KEY_TRAVEL_MODE               = "travel_mode"
        private const val KEY_AVOID_TOLLS               = "avoid_tolls"
        private const val KEY_AVOID_HIGHWAYS            = "avoid_highways"
        private const val KEY_AVOID_FERRIES             = "avoid_ferries"
        private const val KEY_FUEL_EFFICIENT            = "fuel_efficient"
        private const val KEY_SAVED_ROUTES              = "saved_routes"
        /** Saved-route records share one preference string, separated by this. */
        private const val ROUTE_SEPARATOR               = "\n"
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

    /**
     * Fast2SMS API key for internet-based SMS (needs no Android SMS permission).
     * Supplied by FAST2SMS_API_KEY in local.properties — never hardcoded here,
     * because anything in source ends up in git history.
     */
    private val bundledFast2smsKey = com.example.resqgo.BuildConfig.FAST2SMS_API_KEY
    var fast2smsApiKey: String
        get() {
            // If stored value is blank (e.g. installed over old app), use the bundled key
            val stored = prefs.getString(KEY_FAST2SMS_API_KEY, "") ?: ""
            return if (stored.isBlank()) bundledFast2smsKey else stored
        }
        set(value) = prefs.edit().putString(KEY_FAST2SMS_API_KEY, value).apply()
        
    /** Results of the startup readiness checks, surfaced in the sensor panel. */
    var sensorsAvailable: Boolean
        get() = prefs.getBoolean(KEY_SENSORS_AVAILABLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SENSORS_AVAILABLE, value).apply()

    var gpsReady: Boolean
        get() = prefs.getBoolean(KEY_GPS_READY, false)
        set(value) = prefs.edit().putBoolean(KEY_GPS_READY, value).apply()

    /** True once we have asked for SMS permission, so we only ask once. */
    var smsPromptShown: Boolean
        get() = prefs.getBoolean(KEY_SMS_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_SMS_PROMPT_SHOWN, value).apply()

    var sosTimerSeconds: Int
        get() = prefs.getInt(KEY_SOS_TIMER_SECONDS, 30) // Default 30 seconds
        set(value) = prefs.edit().putInt(KEY_SOS_TIMER_SECONDS, value).apply()
    var isDarkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, value).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    // ── Trip / route preferences (Change 10) ────────────────────────────────

    var routePreferences: com.example.resqgo.navigation.RoutePreferences
        get() = com.example.resqgo.navigation.RoutePreferences(
            travelMode = try {
                com.example.resqgo.navigation.TravelMode.valueOf(
                    prefs.getString(KEY_TRAVEL_MODE, null)
                        ?: com.example.resqgo.navigation.TravelMode.TWO_WHEELER.name
                )
            } catch (_: Exception) {
                com.example.resqgo.navigation.TravelMode.TWO_WHEELER
            },
            avoidTolls = prefs.getBoolean(KEY_AVOID_TOLLS, false),
            avoidHighways = prefs.getBoolean(KEY_AVOID_HIGHWAYS, false),
            avoidFerries = prefs.getBoolean(KEY_AVOID_FERRIES, false),
            fuelEfficient = prefs.getBoolean(KEY_FUEL_EFFICIENT, false)
        )
        set(value) = prefs.edit()
            .putString(KEY_TRAVEL_MODE, value.travelMode.name)
            .putBoolean(KEY_AVOID_TOLLS, value.avoidTolls)
            .putBoolean(KEY_AVOID_HIGHWAYS, value.avoidHighways)
            .putBoolean(KEY_AVOID_FERRIES, value.avoidFerries)
            .putBoolean(KEY_FUEL_EFFICIENT, value.fuelEfficient)
            .apply()

    /** Saved routes, newest first, stored as "label|lat|lng". */
    fun getSavedRoutes(): List<String> =
        prefs.getString(KEY_SAVED_ROUTES, "")
            ?.split(ROUTE_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun saveRoute(entry: String) {
        val existing = getSavedRoutes().toMutableList()
        existing.remove(entry)
        existing.add(0, entry)
        prefs.edit()
            .putString(KEY_SAVED_ROUTES, existing.take(20).joinToString(ROUTE_SEPARATOR))
            .apply()
    }

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
