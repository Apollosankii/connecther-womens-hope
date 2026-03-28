package com.womanglobal.connecther.supabase

import android.content.Context

/**
 * Single source of truth for the "authenticated" JWT used by Supabase RPC calls.
 *
 * We store a Supabase-signed JWT minted by the `auth-bridge` Edge Function.
 * (Firebase ID tokens are NOT accepted by Supabase PostgREST as auth JWTs.)
 */
object SupabaseTokenStore {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getAppContextOrNull(): Context? = appContext

    fun getJwtOrNull(): String? {
        val ctx = appContext ?: return null
        return ctx.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("supabase_jwt", null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun setJwt(jwt: String?) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .edit()
            .putString("supabase_jwt", jwt?.trim().orEmpty())
            .apply()
    }

    fun getFirebaseIdTokenOrNull(): String? {
        val ctx = appContext ?: return null
        return ctx.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("firebase_id_token", null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun clear() = setJwt(null)
}

