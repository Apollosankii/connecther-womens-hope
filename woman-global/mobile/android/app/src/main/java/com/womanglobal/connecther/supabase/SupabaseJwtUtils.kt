package com.womanglobal.connecther.supabase

import android.util.Base64
import org.json.JSONObject

object SupabaseJwtUtils {
    private const val DEFAULT_BUFFER_SEC = 300L

    fun secondsUntilExpiry(jwt: String): Long? {
        val exp = decodeExpUnix(jwt) ?: return null
        return exp - System.currentTimeMillis() / 1000
    }

    fun isExpiredOrExpiringSoon(jwt: String, bufferSec: Long = DEFAULT_BUFFER_SEC): Boolean {
        val left = secondsUntilExpiry(jwt) ?: return true
        return left <= bufferSec
    }

    fun isExpired(jwt: String): Boolean {
        val left = secondsUntilExpiry(jwt) ?: return true
        return left <= 0
    }

    /** Bridge JWT `sub` (Firebase UID); matches `users.clerk_user_id` for RLS. */
    fun decodeJwtSub(jwt: String): String? {
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        val json = decodeBase64Url(parts[1]) ?: return null
        return runCatching { JSONObject(json).getString("sub") }.getOrNull()
    }

    private fun decodeExpUnix(jwt: String): Long? {
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        val json = decodeBase64Url(parts[1]) ?: return null
        return runCatching { JSONObject(json).getLong("exp") }.getOrNull()
    }

    private fun decodeBase64Url(segment: String): String? {
        val padded = segment.replace('-', '+').replace('_', '/').let { s ->
            when (s.length % 4) {
                0 -> s
                2 -> "${s}=="
                3 -> "${s}="
                else -> "${s}="
            }
        }
        return runCatching {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }
}
