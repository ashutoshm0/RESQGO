package com.example.resqgo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import org.osmdroid.config.Configuration
import com.example.resqgo.data.local.AppDatabase
import com.example.resqgo.data.local.ThemeMode
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.data.repository.RideRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResqgoApp : Application() {
    // Lazy initialization of Database and Repository
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { RideRepository(database.rideEventDao()) }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        applySavedTheme()
        configureOsmdroid()
    }

    /**
     * osmdroid must be configured before any MapView is inflated. Doing it here
     * rather than in RidingActivity matters: Configuration.load() otherwise scans
     * external storage on the main thread, which stalled the first frame on a
     * cold-start deep link badly enough that the screen never drew.
     * Pinning the cache to app-private storage skips that scan entirely.
     */
    private fun configureOsmdroid() {
        try {
            val base = File(cacheDir, "osmdroid").apply { mkdirs() }
            Configuration.getInstance().apply {
                // OSM blocks placeholder user agents; identify the product.
                userAgentValue = "RESQGO/1.0 (Android two-wheeler safety app)"
                osmdroidBasePath = base
                osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
            }
        } catch (e: Exception) {
            Log.e("ResqgoApp", "osmdroid configuration failed", e)
        }
    }

    /** Restores the rider's Light/Dark choice before the first Activity inflates. */
    private fun applySavedTheme() {
        val mode = try {
            UserPreferences(this).themeMode
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.DARK   -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.LIGHT  -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /**
     * Writes the stack trace of any fatal exception to files/last_crash.txt before
     * the process dies, then hands off to the platform handler so the OS still
     * reports it. RESQGO is sideloaded on riders' phones with no adb attached —
     * without this a crash leaves nothing to diagnose.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(filesDir, LAST_CRASH_FILE).writeText(
                    "RESQGO crash — $stamp\nthread: ${thread.name}\n\n$stack"
                )
            } catch (e: Exception) {
                Log.e("ResqgoApp", "Could not persist crash log", e)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val LAST_CRASH_FILE = "last_crash.txt"

        /** Returns the previous fatal crash trace, or null if the last run was clean. */
        fun readLastCrash(context: Context): String? {
            val f = File(context.filesDir, LAST_CRASH_FILE)
            return if (f.exists()) f.readText() else null
        }

        fun clearLastCrash(context: Context) {
            File(context.filesDir, LAST_CRASH_FILE).delete()
        }
    }
}
