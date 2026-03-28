package com.womanglobal.connecther.supabase

import com.womanglobal.connecther.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AuthBridgeClient {
    data class BridgeResult(
        val supabaseJwt: String,
        val userId: String,
        val firebaseUid: String,
    )

    class BridgeFailure(message: String) : RuntimeException(message)

    suspend fun exchangeFirebaseIdToken(firebaseIdToken: String): Result<BridgeResult> = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = "$supabaseUrl/functions/v1/auth-bridge"

        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer ${firebaseIdToken.trim()}")
            .build()

        val client = OkHttpClient()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        val obj = JSONObject(bodyStr)
                        val detail = obj.optString("detail", "").takeIf { it.isNotBlank() }
                        val err = obj.optString("error", "").takeIf { it.isNotBlank() }
                        listOfNotNull(err, detail).joinToString(" - ").ifBlank { bodyStr }
                    }.getOrDefault(bodyStr)
                    throw BridgeFailure("auth-bridge failed (${resp.code}): $msg")
                }
                val obj = JSONObject(bodyStr)
                val jwt = obj.optString("supabase_jwt", "").trim()
                val userId = obj.optString("user_id", "").trim()
                val firebaseUid = obj.optString("firebase_uid", "").trim()
                if (jwt.isBlank() || userId.isBlank() || firebaseUid.isBlank()) {
                    throw BridgeFailure("auth-bridge missing fields")
                }
                BridgeResult(supabaseJwt = jwt, userId = userId, firebaseUid = firebaseUid)
            }
        }
    }
}

