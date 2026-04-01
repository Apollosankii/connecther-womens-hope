package com.womanglobal.connecther.payment

import android.net.Uri
import android.util.Log
import com.womanglobal.connecther.BuildConfig
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PaystackPaymentRepository {

    data class InitializedCharge(
        val accessCode: String,
        val reference: String,
        val authorizationUrl: String,
    )

    /**
     * Initialize a transaction on the backend so the native PaymentSheet can use [accessCode].
     *
     * Calls Edge Function `paystack-express` with the auth-bridge JWT (`Authorization: Bearer`).
     * Server passes Paystack `channels` (see `PAYSTACK_CHANNELS` on the function).
     */
    suspend fun initializeTransaction(planId: Int, email: String): Result<InitializedCharge> = withContext(Dispatchers.IO) {
        val jwt = SupabaseClientProvider.getCachedJwtOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Missing authenticated JWT (run auth-bridge first)."))

        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = "$supabaseUrl/functions/v1/paystack-checkout"

        val payload = JSONObject()
            .put("plan_id", planId)
            .put("email", email)

        val requestBody = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $jwt")
        val anon = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anon.isNotBlank()) {
            requestBuilder.header("apikey", anon)
        }
        val request = requestBuilder.build()

        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body.string()
                Log.d(TAG, "paystack-express response (${resp.code}): $bodyStr")
                if (!resp.isSuccessful) {
                    throw IllegalStateException("paystack-express init failed (${resp.code}): $bodyStr")
                }
                val obj = JSONObject(bodyStr)

                var accessCode = obj.optString("access_code", "").trim()
                val reference = obj.optString("reference", "").trim()
                val authorizationUrl = obj.optString("authorization_url", "").trim()

                if (accessCode.isBlank() && authorizationUrl.isNotBlank()) {
                    accessCode = extractAccessCodeFromUrl(authorizationUrl)
                    Log.d(TAG, "access_code extracted from authorization_url: $accessCode")
                }

                if (accessCode.isBlank() && authorizationUrl.isBlank()) {
                    throw IllegalStateException("paystack-checkout returned neither access_code nor authorization_url. Response: $bodyStr")
                }
                if (reference.isBlank()) {
                    throw IllegalStateException("paystack-checkout missing reference. Response: $bodyStr")
                }
                InitializedCharge(accessCode = accessCode, reference = reference, authorizationUrl = authorizationUrl)
            }
        }
    }

    /**
     * Confirms payment with Paystack on the server and runs the same DB finalization as the webhook.
     * Call this right after PaymentSheet success so activation is not blocked on a slow webhook.
     */
    suspend fun verifyPaystackTransaction(paymentReference: String): Boolean = withContext(Dispatchers.IO) {
        val jwt = SupabaseClientProvider.getCachedJwtOrNull()
            ?: return@withContext false

        val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val url = "$supabaseUrl/functions/v1/paystack-checkout"

        val payload = JSONObject()
            .put("action", "verify")
            .put("reference", paymentReference)

        val requestBody = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $jwt")
        val anon = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anon.isNotBlank()) {
            requestBuilder.header("apikey", anon)
        }
        val request = requestBuilder.build()

        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body.string()
                Log.d(TAG, "paystack verify response (${resp.code}): $bodyStr")
                if (!resp.isSuccessful) {
                    Log.w(TAG, "paystack verify HTTP ${resp.code}: $bodyStr")
                    return@withContext false
                }
                val obj = JSONObject(bodyStr)
                obj.optBoolean("ok", false) && obj.optBoolean("activated", false)
            }
        }.getOrElse { e ->
            Log.w(TAG, "paystack verify failed", e)
            false
        }
    }

    /**
     * Poll until the subscription row exists (fallback if verify could not run or Paystack was still pending).
     * First check is immediate; uses a shorter interval than legacy webhook-only polling.
     */
    suspend fun waitForSubscriptionActive(
        planId: Int,
        paymentReference: String,
        maxAttempts: Int = 24,
        intervalMs: Long = 750L,
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (com.womanglobal.connecther.supabase.SupabaseData.hasActiveSubscriptionForPayment(planId, paymentReference)) {
                return true
            }
            if (attempt < maxAttempts - 1) {
                delay(intervalMs)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "PaystackPayRepo"

        /**
         * Paystack's `access_code` is the last path segment of `authorization_url`.
         * e.g. `https://checkout.paystack.com/hoib6ip6q5g3nmi` → `hoib6ip6q5g3nmi`
         */
        private fun extractAccessCodeFromUrl(url: String): String {
            return try {
                val uri = Uri.parse(url)
                uri.lastPathSegment.orEmpty().trim()
            } catch (_: Exception) {
                url.substringAfterLast("/").trim()
            }
        }
    }
}
