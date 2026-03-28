package com.womanglobal.connecther.supabase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.tasks.await

/**
 * Supabase clients for ConnectHer.
 *
 * Authenticated calls use the **Firebase ID token** as the JWT on each PostgREST / Storage request
 * (Supabase Third-Party Auth → Firebase). Configure the integration in the Supabase dashboard and
 * ensure users have `role: "authenticated"` in their Firebase custom claims so Postgres uses the
 * `authenticated` role (see Supabase Firebase Auth docs).
 *
 * Note: When [accessToken] is set, the `auth-kt` plugin must **not** be installed on the same client.
 */
object SupabaseClientProvider {

    private const val TAG = "SupabaseClient"

    /** Main client: PostgREST + Storage with Firebase JWT on each request. */
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            accessToken = {
                val user = FirebaseAuth.getInstance().currentUser ?: return@accessToken null
                try {
                    user.getIdToken(false).await().token
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get Firebase ID token for Supabase", e)
                    null
                }
            }
            install(Postgrest)
            install(Storage)
        }
    }

    /** Anon-only client for public reads (no Authorization header). */
    val publicClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Postgrest)
            install(Storage)
        }
    }

    /** @deprecated Token is supplied per-request via [accessToken]; kept for call-site compatibility. */
    fun clearCachedToken() {
        // No-op: Firebase SDK handles token refresh.
    }

    /**
     * Ensures a Firebase user is present and warms the ID token so the next Supabase call has a JWT.
     */
    suspend fun ensureFirebaseSession(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return runCatching {
            user.getIdToken(false).await()
            true
        }.getOrElse {
            Log.e(TAG, "ensureFirebaseSession failed", it)
            false
        }
    }
}
