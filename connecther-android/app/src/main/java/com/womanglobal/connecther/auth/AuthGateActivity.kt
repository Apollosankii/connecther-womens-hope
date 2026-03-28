package com.womanglobal.connecther.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.api.Clerk
import com.clerk.ui.auth.AuthView
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.OnboardingActivity
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.CurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.womanglobal.connecther.R
import com.womanglobal.connecther.ui.theme.ConnectHerTheme
import kotlinx.coroutines.delay

/**
 * Auth gate: shows Clerk sign-in/sign-up when signed out,
 * or routes to Onboarding or Home when signed in.
 *
 * Optimization: When user has signed out, we show the sign-in UI immediately
 * (max 2s wait) instead of waiting for Clerk full init (5–7s).
 */
class AuthGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        val signedOutRecently = prefs.getBoolean(KEY_SIGNED_OUT_RECENTLY, false)

        setContent {
            ConnectHerTheme(dynamicColor = false) {
            val isInitialized by Clerk.isInitialized.collectAsStateWithLifecycle(initialValue = false)
            val user by Clerk.userFlow.collectAsStateWithLifecycle(initialValue = null)
            var showSignInByTimeout by remember { mutableStateOf(false) }

            // After 2 seconds, show sign-in UI even if Clerk hasn't finished (avoids 5–7s wait)
            LaunchedEffect(Unit) {
                if (!signedOutRecently) {
                    delay(MAX_INIT_WAIT_MS)
                    showSignInByTimeout = true
                }
            }

            LaunchedEffect(user) {
                if (user != null) {
                    getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_SIGNED_OUT_RECENTLY, false)
                        .apply()
                    val prefs = getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                    var onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
                    val clerkUserId = user!!.id

                    // If local flag says incomplete, check Supabase for existing profile (returning users)
                    if (!onboardingComplete) {
                        val existingProfile = withContext(Dispatchers.IO) {
                            runCatching { SupabaseData.getUserProfile(clerkUserId) }.getOrNull()
                        }
                        if (existingProfile != null) {
                            saveClerkUserId(this@AuthGateActivity, clerkUserId)
                            markOnboardingComplete(this@AuthGateActivity)
                            CurrentUser.setUser(existingProfile)
                            getSharedPreferences("user_session", Context.MODE_PRIVATE).edit()
                                .putString("user_full_name", "${existingProfile.first_name} ${existingProfile.last_name}")
                                .putString("user_phone", existingProfile.phoneNumber ?: "")
                                .putString("user_email", existingProfile.email ?: "")
                                .putString("user_id", clerkUserId)
                                .putBoolean("isLoggedIn", true)
                                .apply()
                            onboardingComplete = true
                        }
                    }

                    if (onboardingComplete) {
                        startActivity(Intent(this@AuthGateActivity, HomeActivity::class.java))
                    } else {
                        startActivity(Intent(this@AuthGateActivity, OnboardingActivity::class.java))
                    }
                    finish()
                }
            }

            // Show sign-in immediately if: signed out recently, Clerk ready, or timeout reached
            val showSignIn = signedOutRecently || isInitialized || showSignInByTimeout

            // Mark intro seen only after sign-in UI is shown (if we crash earlier, user sees onboarding again)
            LaunchedEffect(showSignIn, user) {
                if (showSignIn && user == null) {
                    getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_INTRO_SEEN, true)
                        .apply()
                }
            }

            when {
                !showSignIn -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                user == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF5EEEC))
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_auth_logo),
                            contentDescription = "ConnectHer",
                            modifier = Modifier.size(80.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ConnectHer",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC2185B)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        AuthView()
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            }
        }
    }

    companion object {
        private const val PREFS_AUTH = "connecther_auth"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_CLERK_USER_ID = "clerk_user_id"
        private const val KEY_SIGNED_OUT_RECENTLY = "signed_out_recently"
        private const val KEY_INTRO_SEEN = "onboarding_intro_seen"
        private const val MAX_INIT_WAIT_MS = 500L  // Show sign-in after 500ms if Clerk not ready (target: UI <3s)

        fun markSignedOut(context: Context) {
            context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SIGNED_OUT_RECENTLY, true)
                .apply()
        }

        fun markOnboardingComplete(context: Context) {
            context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETE, true)
                .apply()
        }

        fun getClerkUserId(context: Context): String? {
            return context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .getString(KEY_CLERK_USER_ID, null)
        }

        fun saveClerkUserId(context: Context, clerkUserId: String) {
            context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CLERK_USER_ID, clerkUserId)
                .apply()
        }
    }
}
