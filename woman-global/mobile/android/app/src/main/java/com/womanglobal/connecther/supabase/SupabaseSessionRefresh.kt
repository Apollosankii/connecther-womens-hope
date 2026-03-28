package com.womanglobal.connecther.supabase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object SupabaseSessionRefresh {

    /**
     * Exchanges a fresh Firebase ID token for a new Supabase JWT via auth-bridge.
     * Updates [user_session] prefs: supabase_jwt, firebase_id_token, user_id, firebase_uid.
     */
    suspend fun tryRefreshSupabaseJwt(context: Context): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val firebaseIdToken = user.awaitIdToken(forceRefresh = true) ?: return false
        val bridge = AuthBridgeClient.exchangeFirebaseIdToken(firebaseIdToken).getOrNull() ?: return false
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        SupabaseTokenStore.setJwt(bridge.supabaseJwt)
        prefs.edit()
            .putString("firebase_id_token", firebaseIdToken)
            .putString("firebase_uid", bridge.firebaseUid)
            .putString("user_id", bridge.userId)
            .apply()
        return true
    }

    private suspend fun FirebaseUser.awaitIdToken(forceRefresh: Boolean): String? =
        suspendCancellableCoroutine { cont ->
            getIdToken(forceRefresh).addOnCompleteListener { task ->
                if (cont.isCancelled) return@addOnCompleteListener
                if (task.isSuccessful) cont.resume(task.result?.token)
                else cont.resume(null)
            }
        }
}
