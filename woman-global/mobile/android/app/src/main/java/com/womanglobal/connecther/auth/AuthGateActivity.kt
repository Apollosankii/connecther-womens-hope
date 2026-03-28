package com.womanglobal.connecther.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.LoginActivity
import com.womanglobal.connecther.OnboardingActivity

/**
 * Legacy entry gate kept for compatibility; app auth is Firebase + auth-bridge.
 * The app now uses `MainActivity` as the launcher; this activity routes using shared preferences.
 */
class AuthGateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean("isFirstLaunch", true)
        val loggedIn = prefs.getBoolean("isLoggedIn", false)

        when {
            firstLaunch -> startActivity(Intent(this, OnboardingActivity::class.java))
            loggedIn -> startActivity(Intent(this, HomeActivity::class.java))
            else -> startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
