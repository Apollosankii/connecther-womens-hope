package com.womanglobal.connecther.supabase

import android.util.Log
import com.clerk.api.Clerk
import com.clerk.api.session.GetTokenOptions
import com.womanglobal.connecther.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.storage.Storage

/**
 * Supabase client that uses Clerk JWT for authenticated requests.
 * Services are public; users/quotes/etc require Clerk JWT for RLS.
 * Caches the Clerk JWT in memory; refreshes when expired or within 5 min of expiry.
 */
object SupabaseClientProvider {

    private const val TAG = "SupabaseClient"
    // Clerk default JWT expires in ~60s; cache for 45s so we never use expired tokens
    private const val TOKEN_EXPIRY_SECONDS = 45L
    private const val REFRESH_BUFFER_MS = 10 * 1000L  // Refresh 10s before cache expiry

    @Volatile
    private var cachedJwt: String? = null

    @Volatile
    private var cachedExpiresAt: Long = 0L

    /** Main client – used for authenticated calls (users, etc.). Session set via ensureClerkSession. */
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth) {
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Storage)
        }
    }

    /**
     * Anon-only client for public tables (services, subscription_plans).
     * Never has importSession – avoids Clerk JWT issues when Supabase JWT settings don't match.
     */
    val publicClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth)
            install(Storage)
        }
    }

    /**
     * Clears the cached Clerk JWT. Call on logout.
     */
    fun clearCachedToken() {
        cachedJwt = null
        cachedExpiresAt = 0L
    }

    /**
     * Sets the Supabase session with the Clerk JWT.
     * Uses in-memory cache when token is still valid (avoids repeated Clerk.getToken calls).
     * Call before authenticated PostgREST requests (getUserProfile, insertUser, etc.).
     */
    suspend fun ensureClerkSession(): Boolean {
        if (Clerk.session == null) {
            clearCachedToken()
            return false
        }

        val now = System.currentTimeMillis()
        val cached = cachedJwt
        if (cached != null && cachedExpiresAt > now + REFRESH_BUFFER_MS) {
            client.auth.importSession(
                UserSession(
                    accessToken = cached,
                    refreshToken = "",
                    expiresIn = TOKEN_EXPIRY_SECONDS,
                    tokenType = "Bearer",
                    user = null
                )
            )
            return true
        }

        return runCatching {
            // Try default template first (always works). "supabase" template is optional
            // and requires setup in Clerk Dashboard; if missing, it fails.
            val result = Clerk.auth.getToken(GetTokenOptions(template = null))
            when (result) {
                is com.clerk.api.network.serialization.ClerkResult.Success -> {
                    val jwt = result.value
                    cachedJwt = jwt
                    cachedExpiresAt = now + (TOKEN_EXPIRY_SECONDS * 1000)
                    client.auth.importSession(
                        UserSession(
                            accessToken = jwt,
                            refreshToken = "",
                            expiresIn = TOKEN_EXPIRY_SECONDS,
                            tokenType = "Bearer",
                            user = null
                        )
                    )
                    true
                }
                is com.clerk.api.network.serialization.ClerkResult.Failure -> {
                    Log.e(TAG, "Failed to fetch Clerk token: ${result.throwable?.message}")
                    false
                }
            }
        }.getOrElse {
            Log.e(TAG, "ensureClerkSession error", it)
            false
        }
    }
}
