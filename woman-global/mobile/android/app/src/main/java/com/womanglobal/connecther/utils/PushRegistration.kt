package com.womanglobal.connecther.utils

import android.content.Context
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Registers FCM token with Supabase after the bridge JWT is available. */
object PushRegistration {
    fun register(context: Context) {
        val deviceId = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseData.upsertFcmToken(token.orEmpty(), deviceId)
            }
        }
    }
}
