package com.womanglobal.connecther.utils

import android.app.Activity
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.BuildConfig
import com.womanglobal.connecther.R

/** Firebase email link (magic link) passwordless sign-in. */
object FirebaseEmailLinkAuth {
    const val PREF_PENDING_EMAIL = "auth_email_link_pending"

    fun buildActionCodeSettings(activity: Activity): ActionCodeSettings =
        ActionCodeSettings.newBuilder()
            .setUrl(activity.getString(R.string.auth_email_link_continue_url))
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                activity.packageName,
                true,
                BuildConfig.VERSION_CODE.toString(),
            )
            .build()

    fun sendSignInLink(
        auth: FirebaseAuth,
        email: String,
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        auth.sendSignInLinkToEmail(email, buildActionCodeSettings(activity))
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onSuccess()
                else onFailure(task.exception?.message ?: "send failed")
            }
    }
}
