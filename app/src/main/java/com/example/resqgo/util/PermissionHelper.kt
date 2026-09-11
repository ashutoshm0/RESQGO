package com.example.resqgo.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Single source of truth for the runtime permissions RESQGO needs.
 *
 * Android 14+ refuses to start a `location` foreground service unless the
 * location runtime permission is already granted — it throws SecurityException,
 * which crashed the app on first launch. Everything that starts monitoring must
 * check [hasLocation] first.
 */
object PermissionHelper {

    /** Permissions we request up-front, in one dialog batch. */
    fun startupPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE
            // SEND_SMS intentionally NOT requested — it triggers Play Protect
            // warnings on sideloaded builds. Riders grant it manually in Settings.
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Coarse OR fine — either is enough to keep the foreground service legal. */
    fun hasLocation(context: Context): Boolean =
        has(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        has(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasFineLocation(context: Context): Boolean =
        has(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        has(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        has(context, Manifest.permission.POST_NOTIFICATIONS)

    fun hasCallPhone(context: Context): Boolean =
        has(context, Manifest.permission.CALL_PHONE)

    fun hasSendSms(context: Context): Boolean =
        has(context, Manifest.permission.SEND_SMS)

    /**
     * Requested separately, at ride start rather than first launch: asking for SMS
     * in the opening batch looks alarming and trips Play Protect heuristics on
     * sideloaded builds. Without it the SIM SOS layer can never fire.
     */
    fun smsPermission(): Array<String> = arrayOf(Manifest.permission.SEND_SMS)

    /** Everything required before a ride may start. */
    fun missingForRide(context: Context): List<String> = buildList {
        if (!hasLocation(context)) add("Location")
        if (!hasNotifications(context)) add("Notifications")
    }

    /** Opens this app's page in system Settings (the manual grant path). */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
