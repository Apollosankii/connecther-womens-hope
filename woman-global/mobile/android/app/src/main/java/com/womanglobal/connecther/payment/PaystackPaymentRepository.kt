package com.womanglobal.connecther.payment

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

class PaystackPaymentRepository {

    data class InitializedCharge(
        val accessCode: String,
        val reference: String,
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
        val url = "$supabaseUrl/functions/v1/paystack-express"

        val payload = JSONObject()
            .put("plan_id", planId)
            .put("email", email)

        val requestBody = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $jwt")
            .build()

        val client = OkHttpClient()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body.string()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("paystack-express init failed (${resp.code}): $bodyStr")
                }
                val obj = JSONObject(bodyStr)
                val accessCode = obj.optString("access_code", "")
                val reference = obj.optString("reference", "")
                if (accessCode.isBlank() || reference.isBlank()) {
                    throw IllegalStateException("paystack-express missing access_code/reference: $bodyStr")
                }
                InitializedCharge(accessCode = accessCode, reference = reference)
            }
        }
    }

    /**
     * Poll until webhook activation creates the subscription row, or [maxAttempts] exhausted.
     */
    suspend fun waitForSubscriptionActive(planId: Int, paymentReference: String, maxAttempts: Int = 30): Boolean {
        repeat(maxAttempts) {
            if (com.womanglobal.connecther.supabase.SupabaseData.hasActiveSubscriptionForPayment(planId, paymentReference)) {
                return true
            }
            delay(2_000)
        }
        return false
    }
}
