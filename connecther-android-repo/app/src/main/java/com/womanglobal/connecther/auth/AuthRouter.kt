package com.womanglobal.connecther.auth

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.OnboardingActivity
import com.womanglobal.connecther.repository.SupabaseRepository
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.CurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * After Firebase authentication succeeds, syncs the user into Supabase and navigates to
 * [OnboardingActivity] or [HomeActivity] based on onboarding state / existing profile.
 */
object AuthRouter {

    suspend fun routeAfterSignIn(activity: ComponentActivity) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = firebaseUser.uid
        val email = firebaseUser.email

        SupabaseRepository().syncAuthenticatedFirebaseUser(uid, email)

        AuthPreferences.saveFirebaseUid(activity, uid)
        AuthPreferences.clearSignedOutRecently(activity)

        val authPrefs = activity.getSharedPreferences(AuthPreferences.PREFS_AUTH, Context.MODE_PRIVATE)
        var onboardingComplete = authPrefs.getBoolean(AuthPreferences.KEY_ONBOARDING_COMPLETE, false)

        if (!onboardingComplete) {
            val existingProfile = withContext(Dispatchers.IO) {
                runCatching { SupabaseData.getUserProfile(uid) }.getOrNull()
            }
            if (existingProfile != null) {
                AuthPreferences.markOnboardingComplete(activity)
                CurrentUser.setUser(existingProfile)
                activity.getSharedPreferences("user_session", Context.MODE_PRIVATE).edit()
                    .putString("user_full_name", "${existingProfile.first_name} ${existingProfile.last_name}")
                    .putString("user_phone", existingProfile.phoneNumber ?: "")
                    .putString("user_email", existingProfile.email ?: "")
                    .putString("user_id", uid)
                    .putBoolean("isLoggedIn", true)
                    .apply()
                onboardingComplete = true
            }
        }

        if (onboardingComplete) {
            activity.startActivity(Intent(activity, HomeActivity::class.java))
        } else {
            activity.startActivity(Intent(activity, OnboardingActivity::class.java))
        }
        activity.finish()
    }
}
