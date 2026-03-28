package com.womanglobal.connecther.auth

import android.content.Context

/** Shared preferences for auth routing (onboarding flags, cached Firebase uid, signed-out UX). */
object AuthPreferences {

    const val PREFS_AUTH = "connecther_auth"

    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    /** Legacy key name; older builds stored the external auth id here (now Firebase UID). */
    private const val KEY_LEGACY_EXTERNAL_ID = "clerk_user_id"
    private const val KEY_FIREBASE_UID = "firebase_uid"
    private const val KEY_SIGNED_OUT_RECENTLY = "signed_out_recently"
    const val KEY_INTRO_SEEN = "onboarding_intro_seen"

    fun getFirebaseUid(context: Context): String? {
        val p = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        return p.getString(KEY_FIREBASE_UID, null) ?: p.getString(KEY_LEGACY_EXTERNAL_ID, null)
    }

    fun saveFirebaseUid(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit()
            .putString(KEY_FIREBASE_UID, uid)
            .remove(KEY_LEGACY_EXTERNAL_ID)
            .apply()
    }

    fun markSignedOut(context: Context) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNED_OUT_RECENTLY, true)
            .apply()
    }

    fun clearSignedOutRecently(context: Context) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SIGNED_OUT_RECENTLY, false)
            .apply()
    }

    fun markOnboardingComplete(context: Context) {
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    fun isSignedOutRecently(context: Context): Boolean =
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIGNED_OUT_RECENTLY, false)
}
