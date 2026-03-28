package com.womanglobal.connecther.utils

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectHerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyTheme(this)
        ServiceBuilder.init(this)  // Initialize ServiceBuilder here
        CurrentUser.initialize(this)
        SupabaseTokenStore.init(this)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val deviceId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            ).orEmpty()
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseData.upsertFcmToken(token.orEmpty(), deviceId)
            }
        }
    }
}
