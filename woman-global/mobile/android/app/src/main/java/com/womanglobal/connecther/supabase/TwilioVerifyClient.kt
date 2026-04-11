package com.womanglobal.connecther.supabase

import com.womanglobal.connecther.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Twilio Verify via Supabase Edge Function [phone-verify].
 * Requires [BuildConfig.SUPABASE_URL] and a valid Firebase ID token (Bearer).
 */
object TwilioVerifyClient {

    class VerifyException(val code: String?, message: String) : RuntimeException(message)

    private fun parseErrorMessage(bodyStr: String, httpCode: Int): String {
        return runCatching {
            val obj = JSONObject(bodyStr)
            val c = obj.optString("code", "").takeIf { it.isNotBlank() }
            val err = obj.optString("error", "").takeIf { it.isNotBlank() }
            val detail = obj.optString("detail", "").takeIf { it.isNotBlank() }
            listOfNotNull(c, err, detail).joinToString(" — ").ifBlank { "HTTP $httpCode" }
        }.getOrDefault(bodyStr.ifBlank { "HTTP $httpCode" })
    }

    suspend fun start(firebaseIdToken: String, phoneE164: String): Result<Unit> = withContext(Dispatchers.IO) {
        postVerify(firebaseIdToken, """{"action":"start","phone":${JSONObject.quote(phoneE164)}}""")
    }

    suspend fun check(firebaseIdToken: String, phoneE164: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        postVerify(
            firebaseIdToken,
            """{"action":"check","phone":${JSONObject.quote(phoneE164)},"code":${JSONObject.quote(code)}}""",
        )
    }

    private fun postVerify(firebaseIdToken: String, jsonBody: String): Result<Unit> {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = "$supabaseUrl/functions/v1/phone-verify"
        val anon = BuildConfig.SUPABASE_ANON_KEY.trim()
        val firebaseToken = firebaseIdToken.trim()
        // Supabase API gateway may validate Authorization as a Supabase JWT and return 401 if we send a Firebase token there.
        // Use anon key + apikey for the gateway; send Firebase token in X-Firebase-Id-Token (see phone-verify Edge function).
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

        return runCatching {
            OkHttpClient().newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw VerifyException(null, parseErrorMessage(bodyStr, resp.code))
                }
                val obj = JSONObject(bodyStr)
                if (!obj.optBoolean("ok", false)) {
                    throw VerifyException(
                        obj.optString("code").takeIf { it.isNotBlank() },
                        parseErrorMessage(bodyStr, resp.code),
                    )
                }
            }
        }
    }
}
