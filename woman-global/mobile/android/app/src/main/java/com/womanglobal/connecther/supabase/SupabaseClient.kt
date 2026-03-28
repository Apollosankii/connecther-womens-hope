package com.womanglobal.connecther.supabase

import android.util.Log
import com.womanglobal.connecther.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Supabase PostgREST client authenticated with a JWT from [AuthBridgeClient] (Firebase ID token exchange).
 * Public data uses [publicClient]; user-scoped tables/RPCs need this JWT and RLS on `users`.
 */
object SupabaseClientProvider {

    private const val TAG = "SupabaseClient"
    private const val TOKEN_EXPIRY_SECONDS = 45L
    private const val REFRESH_BUFFER_MS = 10 * 1000L  // Refresh 10s before cache expiry

    @Volatile
    private var cachedJwt: String? = null

    @Volatile
    private var cachedExpiresAt: Long = 0L

    /**
     * Returns the cached Supabase JWT (minted by auth-bridge; JWT `sub` is Firebase UID).
     */
    fun getCachedJwtOrNull(): String? = SupabaseTokenStore.getJwtOrNull() ?: cachedJwt

    /** Stores the bridge JWT for PostgREST and Edge calls that expect `Authorization: Bearer`. */
    fun setCachedJwt(jwt: String?, expiresAtSeconds: Long? = null) {
        cachedJwt = jwt
        cachedExpiresAt = (expiresAtSeconds ?: 0L) * 1000L
        if (!jwt.isNullOrBlank()) {
            SupabaseTokenStore.setJwt(jwt)
        }
    }

    /** Authenticated client; token from auth-bridge. Call [ensureSupabaseSession] before RPCs when the app may have stale JWTs. */
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Storage)

            // Bridge JWT (not Supabase Auth session). supabase-kt forbids install(Auth) with custom accessToken.
            // DB: auth.jwt()->>'sub' = Firebase UID, stored in legacy column users.clerk_user_id.
            accessToken = {
                SupabaseTokenStore.getJwtOrNull().orEmpty()
            }
        }
    }

    /**
     * Anon-only client for public tables (services, subscription_plans).
     */
    val publicClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Storage)
        }
    }

    /** Clears cached bridge JWT. Call on logout (prefs should also remove `supabase_jwt`). */
    fun clearCachedToken() {
        cachedJwt = null
        cachedExpiresAt = 0L
        SupabaseTokenStore.clear()
    }

    /**
     * Ensures a valid Supabase JWT for PostgREST (minted by auth-bridge, ~1h TTL).
     * Refreshes from Firebase via auth-bridge when missing or near expiry.
     */
    suspend fun ensureSupabaseSession(): Boolean {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return false
        val ctx = SupabaseTokenStore.getAppContextOrNull() ?: return false

        var jwt = SupabaseTokenStore.getJwtOrNull()
        if (jwt.isNullOrBlank()) {
            return SupabaseSessionRefresh.tryRefreshSupabaseJwt(ctx) &&
                !SupabaseTokenStore.getJwtOrNull().isNullOrBlank()
        }
        if (SupabaseJwtUtils.isExpiredOrExpiringSoon(jwt)) {
            val refreshed = SupabaseSessionRefresh.tryRefreshSupabaseJwt(ctx)
            if (refreshed) return true
            return !SupabaseJwtUtils.isExpired(jwt)
        }
        return true
    }
}
