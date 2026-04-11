package com.womanglobal.connecther.data.local

import android.content.Context
import com.google.gson.Gson
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.SubscriptionPackage
import com.womanglobal.connecther.services.ChatMessage
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private data class ActiveSubBlob(
    val planId: Int,
    val planName: String,
    val expiresAt: String?,
    val connectsGranted: Int? = null,
    val connectsUsed: Int? = null,
)

/**
 * Offline read-through cache using app-private SharedPreferences (no Room / kapt — avoids flaky Maven downloads).
 */
object AppOfflineCache {
    private const val PREFS_NAME = "connecther_offline_cache"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val gson = Gson()

    private const val KEY_BOOKINGS = "v1:my_booking_requests"
    private const val KEY_PLANS = "v1:subscription_plans"
    private const val KEY_ACTIVE_SUB = "v2:active_subscription"
    private const val KEY_PENDING_JOBS = "v2:pending_jobs"
    private const val KEY_CONVERSATIONS = "v1:conversations"
    private const val KEY_COMPLETED_JOBS = "v2:completed_jobs"

    fun messagesKey(chatCode: String) = "v1:chat_messages:${chatCode.trim()}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun readBookingRequests(context: Context): List<SupabaseData.MyBookingRequest>? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_BOOKINGS, null) ?: return@withContext null
        runCatching {
            json.decodeFromString(ListSerializer(SupabaseData.MyBookingRequest.serializer()), raw)
        }.getOrNull()
    }

    suspend fun writeBookingRequests(context: Context, rows: List<SupabaseData.MyBookingRequest>) = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(ListSerializer(SupabaseData.MyBookingRequest.serializer()), rows)
        prefs(context).edit().putString(KEY_BOOKINGS, raw).apply()
    }

    suspend fun readChatMessages(context: Context, chatCode: String): List<ChatMessage>? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(messagesKey(chatCode), null) ?: return@withContext null
        runCatching { gson.fromJson(raw, Array<ChatMessage>::class.java)?.toList() }.getOrNull()
    }

    suspend fun writeChatMessages(context: Context, chatCode: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(messagesKey(chatCode), gson.toJson(messages)).apply()
    }

    suspend fun readSubscriptionPlans(context: Context): List<SubscriptionPackage>? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_PLANS, null) ?: return@withContext null
        runCatching { gson.fromJson(raw, Array<SubscriptionPackage>::class.java)?.toList() }.getOrNull()
    }

    suspend fun writeSubscriptionPlans(context: Context, plans: List<SubscriptionPackage>) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_PLANS, gson.toJson(plans)).apply()
    }

    suspend fun readActiveSubscription(context: Context): SupabaseData.ActiveSubscription? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_ACTIVE_SUB, null)
            ?: prefs(context).getString("v1:active_subscription", null)
            ?: return@withContext null
        val blob = runCatching { gson.fromJson(raw, ActiveSubBlob::class.java) }.getOrNull() ?: return@withContext null
        SupabaseData.ActiveSubscription(
            blob.planId,
            blob.planName,
            blob.expiresAt,
            blob.connectsGranted,
            blob.connectsUsed,
        )
    }

    suspend fun writeActiveSubscription(context: Context, sub: SupabaseData.ActiveSubscription?) = withContext(Dispatchers.IO) {
        val e = prefs(context).edit()
        if (sub == null) {
            e.remove(KEY_ACTIVE_SUB).apply()
            return@withContext
        }
        val blob = ActiveSubBlob(
            sub.planId,
            sub.planName,
            sub.expiresAt,
            sub.connectsGranted,
            sub.connectsUsed,
        )
        e.putString(KEY_ACTIVE_SUB, gson.toJson(blob)).apply()
    }

    suspend fun readPendingJobs(context: Context): List<Job>? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_PENDING_JOBS, null) ?: return@withContext null
        runCatching { gson.fromJson(raw, Array<Job>::class.java)?.toList() }.getOrNull()
    }

    suspend fun writePendingJobs(context: Context, jobs: List<Job>) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_PENDING_JOBS, gson.toJson(jobs)).apply()
    }

    suspend fun readCompletedJobs(context: Context): List<Job>? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_COMPLETED_JOBS, null) ?: return@withContext null
        runCatching { gson.fromJson(raw, Array<Job>::class.java)?.toList() }.getOrNull()
    }

    suspend fun writeCompletedJobs(context: Context, jobs: List<Job>) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_COMPLETED_JOBS, gson.toJson(jobs)).apply()
    }

    suspend fun readConversations(context: Context): List<Conversation>? = withContext(Dispatchers.IO) {
        val raw = prefs(context).getString(KEY_CONVERSATIONS, null) ?: return@withContext null
        runCatching { gson.fromJson(raw, Array<Conversation>::class.java)?.toList() }.getOrNull()
    }

    suspend fun writeConversations(context: Context, rows: List<Conversation>) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_CONVERSATIONS, gson.toJson(rows)).apply()
    }
}
