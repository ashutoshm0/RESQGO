package com.example.resqgo.ui.confirmation

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.resqgo.R
import com.example.resqgo.databinding.ActivityConfirmationBinding
import com.example.resqgo.service.RideMonitoringService
import com.example.resqgo.sos.SOSManager
import java.util.Locale

class ConfirmationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityConfirmationBinding
    private var countDownTimer: CountDownTimer? = null
    private var secondsLeft = 15
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private lateinit var sosManager: SOSManager
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this) {
            // Prevent back button from canceling without confirmation
        }

        sosManager = SOSManager(this)
        tts = TextToSpeech(this, this)

        setupAlerts()
        startCountdown()

        binding.btnImOk.setOnClickListener {
            cancelAlert()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                speakAlert()
            }
        }
    }

    private fun speakAlert() {
        if (isTtsReady) {
            tts?.speak("Possible accident detected. Tap I'm OK or we will call for help in $secondsLeft seconds.", TextToSpeech.QUEUE_FLUSH, null, "alert")
        }
    }

    private fun setupAlerts() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
        }

        try {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmSound)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var initialSeconds = 15

    private fun startCountdown() {
        val prefs = com.example.resqgo.data.local.UserPreferences(this)
        secondsLeft = prefs.sosTimerSeconds
        initialSeconds = secondsLeft
        
        binding.tvCountdownText.text = secondsLeft.toString()
        countDownTimer = object : CountDownTimer((secondsLeft * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000).toInt()
                binding.tvCountdownText.text = secondsLeft.toString()
                
                // Keep the circular progress synced with the dynamic duration
                binding.countdownRing.progress = millisUntilFinished / (initialSeconds * 1000f)
                
                // Only speak the alert when 10 seconds are remaining
                if (secondsLeft == 10) {
                    speakAlert()
                }
            }

            override fun onFinish() {
                executeSOS()
            }
        }.start()
    }

    private fun cancelAlert() {
        stopAlerts()
        RideMonitoringService.resetConfirmationFlag()
        finish()
    }

    private fun executeSOS() {
        stopAlerts()
        RideMonitoringService.resetConfirmationFlag()
        sosManager.triggerSOS(manuallyTriggered = false)
    }

    private fun stopAlerts() {
        countDownTimer?.cancel()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        tts?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlerts()
        tts?.shutdown()
    }
}
