package com.example.resqgo.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.resqgo.R
import com.example.resqgo.data.local.UserPreferences
import com.example.resqgo.ui.home.HomeActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Wait 1.5 seconds, then check onboarding status
        lifecycleScope.launch {
            delay(1500)
            
            val prefs = UserPreferences(this@SplashActivity)
            if (prefs.hasCompletedOnboarding) {
                startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
            }
            finish()
        }
    }
}
