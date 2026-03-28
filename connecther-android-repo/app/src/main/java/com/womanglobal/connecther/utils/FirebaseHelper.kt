package com.womanglobal.connecther.utils

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FirebaseHelper {
    suspend fun getFCMToken(onTokenReceived: (String?) -> Unit) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            onTokenReceived(token)
        } catch (e: Exception) {
            Log.w(TAG, "Fetching FCM token failed", e)
            onTokenReceived(null)
        }
    }

    private const val TAG = "FirebaseHelper"
}
