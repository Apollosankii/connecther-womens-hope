package com.womanglobal.connecther.supabase

import com.womanglobal.connecther.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * GBV panic SMS via Supabase Edge [panic-sms] (Twilio Messaging, subscription-gated server-side).
 * Same gateway pattern as [TwilioVerifyClient]: anon + apikey, Firebase token in X-Firebase-Id-Token.
 */
object PanicSmsClient {

    class PanicSmsException(val code: String?, message: String) : RuntimeException(message)

    private fun parseErrorMessage(bodyStr: String, httpCode: Int): Pair<String?, String> {
        return runCatching {
            val obj = JSONObject(bodyStr)
            val c = obj.optString("code", "").takeIf { it.isNotBlank() }
            val err = obj.optString("error", "").takeIf { it.isNotBlank() }
            val detail = obj.optString("detail", "").takeIf { it.isNotBlank() }
            c to listOfNotNull(err, detail).joinToString(" — ").ifBlank { "HTTP $httpCode" }
        }.getOrDefault(null to (bodyStr.ifBlank { "HTTP $httpCode" }))
    }

    /** @param recipientsE164 Already normalized E.164 (max 5). */
    suspend fun send(
        firebaseIdToken: String,
        recipientsE164: List<String>,
        latitude: Double?,
        longitude: Double?,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val trimmed = recipientsE164.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(PanicSmsException("NO_RECIPIENTS", "No recipients"))
        }
        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = "$supabaseUrl/functions/v1/panic-sms"
        val anon = BuildConfig.SUPABASE_ANON_KEY.trim()
        val firebaseToken = firebaseIdToken.trim()
        val json = JSONObject().put("recipients", JSONArray(trimmed))
        if (latitude != null && longitude != null) {
            json.put("latitude", latitude).put("longitude", longitude)
        }
        val jsonBody = json.toString()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("X-Firebase-Id-Token", firebaseToken)
            .apply {
                if (anon.isNotBlank()) {
                    header("Authorization", "Bearer $anon")
                    header("apikey", anon)
                } else {
                    header("Authorization", "Bearer $firebaseToken")
                }
            }
            .build()

        runCatching {
            OkHttpClient().newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val (c, msg) = parseErrorMessage(bodyStr, resp.code)
                    throw PanicSmsException(c, msg)
                }
                val obj = JSONObject(bodyStr)
                if (!obj.optBoolean("ok", false)) {
                    val (c, msg) = parseErrorMessage(bodyStr, resp.code)
                    throw PanicSmsException(c, msg)
                }
                obj.optInt("sent_count", trimmed.size).coerceAtLeast(1)
            }
        }
    }
}
