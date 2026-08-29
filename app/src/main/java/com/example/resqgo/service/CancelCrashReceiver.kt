package com.example.resqgo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log

/**
 * Handles the "I'M OK" action button in the crash notification.
 * Dismisses the notification and resets the confirmation flag.
 */
class CancelCrashReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CancelCrashReceiver", "User tapped I'M OK from notification")
        RideMonitoringService.resetConfirmationFlag()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(911) // CRASH_NOTIFICATION_ID
    }
}
