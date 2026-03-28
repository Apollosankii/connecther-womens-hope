package com.womanglobal.connecther

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.womanglobal.connecther.onboarding.OnboardingPage
import com.womanglobal.connecther.onboarding.OnboardingPagerAdapter
import com.womanglobal.connecther.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_session", MODE_PRIVATE)

        val pages = listOf(
            OnboardingPage(
                imageRes = R.drawable.onboarding_welcome,
                title = "Welcome to ConnectHer",
                body = getString(R.string.onboarding_page_desc),
            ),
            OnboardingPage(
                imageRes = R.drawable.onboarding_services,
                title = "Trusted services",
                body = "Find house cleaners, caregivers, and more — from verified professionals near you.",
            ),
            OnboardingPage(
                imageRes = R.drawable.onboarding_how_it_works,
                title = "How it works",
                body = "Browse services, choose a provider, and book at your convenience — safely and quickly.",
            ),
            OnboardingPage(
                imageRes = R.drawable.onboarding_get_started,
                title = "Ready?",
                body = "Create an account or sign in to continue.",
            ),
        )

        binding.viewPager.adapter = OnboardingPagerAdapter(pages)

        TabLayoutMediator(binding.pageDots, binding.viewPager) { _, _ -> }.attach()

        fun completeOnboarding() {
            prefs.edit().putBoolean("isFirstLaunch", false).apply()
        }

        fun goLogin() {
            completeOnboarding()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        fun goSignup() {
            completeOnboarding()
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.skipButton.setOnClickListener { goLogin() }
        binding.loginButton.setOnClickListener { goLogin() }
        binding.signupButton.setOnClickListener { goSignup() }

        binding.nextButton.setOnClickListener {
            val next = binding.viewPager.currentItem + 1
            if (next < pages.size) {
                binding.viewPager.currentItem = next
            } else {
                goLogin()
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.nextButton.text = if (position == pages.lastIndex) "Continue" else "Next"
            }
        })
    }
}

