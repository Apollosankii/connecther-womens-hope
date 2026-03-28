package com.womanglobal.connecther

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.womanglobal.connecther.databinding.ActivityMainBinding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        val firstLaunch = sharedPreferences.getBoolean("isFirstLaunch", true)
        val loggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        // Route immediately; this screen is just the launch/splash gate.
        when {
            firstLaunch -> startActivity(Intent(this, OnboardingActivity::class.java))
            loggedIn -> startActivity(Intent(this, HomeActivity::class.java))
            else -> startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
