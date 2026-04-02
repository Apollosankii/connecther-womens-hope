package com.womanglobal.connecther.supabase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.womanglobal.connecther.BuildConfig
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.data.SubscriptionPackage
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.utils.CurrentUser
import com.womanglobal.connecther.services.ChatMessage
import com.womanglobal.connecther.services.Conversation
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

object SupabaseData {

    /** Must match `supabase_storage_profpics_and_upload_fix.sql` bucket id and public URL path. */
    private const val PROFILE_PHOTOS_BUCKET = "profpics"

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() &&
            !BuildConfig.SUPABASE_URL.contains("replace") &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            !BuildConfig.SUPABASE_ANON_KEY.contains("replace")

    /** Notes + Google Maps search line when GPS is present (stored on booking_requests.location_text). */
    private fun buildBookingLocationNotes(
        locationText: String?,
        latitude: Double?,
        longitude: Double?,
    ): String {
        val base = locationText?.trim()?.takeIf { it.isNotEmpty() }
        val link = if (latitude != null && longitude != null) {
            "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        } else {
            null
        }
        return when {
            base != null && link != null -> "$base\n$link"
            base != null -> base
            link != null -> link
            else -> ""
        }
    }

    data class BookingOutcome(
        val isSuccess: Boolean,
        val errorCode: String? = null,
    )

    /**
     * PostgREST wraps each row as `{ "get_my_booking_requests": { ... } }` for `RETURNS SETOF jsonb`.
     */
    @Serializable
    private data class MyBookingRequestRpcEnvelope(
        @SerialName("get_my_booking_requests") val get_my_booking_requests: MyBookingRequest? = null,
    )

    @Serializable
    data class MyBookingRequest(
        val id: Long,
        val status: String,
        val role: String,
        val expires_at: String? = null,
        val proposed_price: Double? = null,
        val location_text: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        @SerialName("maps_url") val maps_url: String? = null,
        val message: String? = null,
        val service_id: Int? = null,
        val client_display: String? = null,
        val provider_display: String? = null,
        val created_at: String? = null,
    )

    data class BookingActionOutcome(
        val isSuccess: Boolean,
        val errorCode: String? = null,
        val chatCode: String? = null,
        val quoteId: String? = null,
    )

    /** Creates a booking via Supabase RPC `create_booking_request` (requires Firebase-linked Supabase JWT). */
    suspend fun createBookingRequest(
        providerRef: String,
        serviceId: Int,
        proposedPrice: Double,
        locationText: String?,
        latitude: Double?,
        longitude: Double?,
        message: String?,
    ): BookingOutcome {
        if (!isConfigured()) return BookingOutcome(isSuccess = false, errorCode = "supabase_not_configured")
        if (!SupabaseClientProvider.ensureSupabaseSession()) {
            return BookingOutcome(isSuccess = false, errorCode = "auth_required")
        }

        @Serializable
        data class CreateBookingRpcRow(
            @SerialName("request_id") val request_id: Long? = null,
            @SerialName("err") val err: String? = null,
        )

        val client = SupabaseClientProvider.client

        suspend fun invokeRpc(): BookingOutcome {
            val locationForRpc = buildBookingLocationNotes(locationText, latitude, longitude)
            val params = buildJsonObject {
                put("p_provider_ref", providerRef)
                put("p_service_id", serviceId)
                put("p_proposed_price", proposedPrice)
                put("p_location_text", locationForRpc)
                latitude?.let { put("p_lat", it) }
                longitude?.let { put("p_lng", it) }
                put("p_message", message ?: "")
            }
            val rows = client.postgrest.rpc("create_booking_request", params).decodeList<CreateBookingRpcRow>()
            val row = rows.firstOrNull()
            return if (row?.request_id != null && row.err.isNullOrBlank()) {
                BookingOutcome(isSuccess = true)
            } else {
                BookingOutcome(isSuccess = false, errorCode = row?.err?.ifBlank { null } ?: "request_failed")
            }
        }

        return runCatching { invokeRpc() }.getOrElse { e ->
            if (isJwtExpiredError(e)) {
                return runCatching {
                    if (!SupabaseClientProvider.ensureSupabaseSession()) {
                        BookingOutcome(isSuccess = false, errorCode = "auth_required")
                    } else {
                        invokeRpc()
                    }
                }.getOrElse { e2 ->
                    val code = mapBookingErrorCode(e2)
                    Log.e("SupabaseData", "createBookingRequest retry failed: $code", e2)
                    BookingOutcome(isSuccess = false, errorCode = code)
                }
            }
            val code = mapBookingErrorCode(e)
            Log.e("SupabaseData", "createBookingRequest failed: $code", e)
            BookingOutcome(isSuccess = false, errorCode = code)
        }
    }

    /** User-facing message when a booking fails (uses string resources for connect-limit cases). */
    fun bookingRequestErrorMessage(context: Context, code: String?): String {
        return when (code) {
            "supabase_not_configured" -> "Supabase is not configured on this build."
            "not_authenticated" -> "Please sign in again to continue."
            "auth_required" -> "Please sign in again to continue."
            "provider_not_found_or_unavailable" -> "Provider is unavailable. Please choose another provider."
            "provider_not_found" -> "Provider is unavailable. Please choose another provider."
            "provider_not_subscribed_to_service" -> "This provider is not subscribed to this service."
            "provider_busy" -> "Provider is currently unavailable for booking."
            "duplicate_booking_same_provider" ->
                context.getString(R.string.booking_error_duplicate_same_provider)
            "cannot_book_self" -> "You cannot book yourself."
            "free_tier_exhausted" -> context.getString(R.string.booking_error_free_tier_exhausted)
            "connects_exhausted" -> context.getString(R.string.booking_error_connects_exhausted)
            "insufficient_connects", "subscription_required" ->
                context.getString(R.string.booking_error_subscribe_for_connects)
            "not_implemented" -> "Booking is temporarily unavailable. Please try again later."
            else -> "Booking request failed. Please try again."
        }
    }

    /** True when the user should be nudged to open Subscriptions for more connects. */
    fun isBookingConnectLimitError(code: String?): Boolean = when (code) {
        "free_tier_exhausted", "connects_exhausted", "insufficient_connects", "subscription_required" -> true
        else -> false
    }

    private fun mapBookingErrorCode(e: Throwable): String {
        var t: Throwable? = e
        while (t != null) {
            val msg = (t.message ?: "").lowercase()
            when {
                msg.contains("jwt") || msg.contains("auth") || msg.contains("not authenticated") -> return "auth_required"
                msg.contains("provider_not_found_or_unavailable") -> return "provider_not_found_or_unavailable"
                msg.contains("provider_not_found") -> return "provider_not_found"
                msg.contains("duplicate_booking_same_provider") -> return "duplicate_booking_same_provider"
                msg.contains("cannot_book_self") -> return "cannot_book_self"
                msg.contains("provider_busy") || msg.contains("available_for_booking") -> return "provider_busy"
                msg.contains("free_tier_exhausted") -> return "free_tier_exhausted"
                msg.contains("connects_exhausted") -> return "connects_exhausted"
                msg.contains("insufficient_connects") -> return "insufficient_connects"
                msg.contains("subscription") -> return "subscription_required"
                msg.contains("permission denied") || msg.contains("rls") -> return "auth_required"
            }
            t = t.cause
        }
        return "request_failed"
    }

    /**
     * Returns true if the exception indicates the user already exists (unique on legacy `users.clerk_user_id`, Firebase UID).
     * Used to treat duplicate-insert as success when user signed in on another device.
     */
    fun isUserAlreadyExistsError(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            val msg = t.message ?: ""
            if (msg.contains("23505") || msg.contains("duplicate key") || msg.contains("unique constraint")) return true
            t = t.cause
        }
        return false
    }

    /** Returns true if the exception indicates JWT expired. Clears token cache when detected. */
    fun isJwtExpiredError(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            val msg = t.message ?: ""
            if (msg.contains("JWT expired", ignoreCase = true)) {
                SupabaseClientProvider.clearCachedToken()
                return true
            }
            t = t.cause
        }
        return false
    }

    /**
     * Register FCM token with Supabase. Call after Firebase sign-in + auth-bridge.
     * Requires supabase_devices_fcm.sql (upsert_my_device RPC) to be run in Supabase.
     */
    suspend fun upsertFcmToken(regToken: String, deviceId: String): Boolean {
        if (!isConfigured() || regToken.isBlank()) return false
        return runCatching {
            if (!SupabaseClientProvider.ensureSupabaseSession()) return@runCatching false
            val client = SupabaseClientProvider.client
            client.postgrest.rpc(
                "upsert_my_device",
                buildJsonObject {
                    put("p_reg_token", regToken)
                    put("p_device", deviceId.ifBlank { "default" })
                }
            )
            true
        }.getOrElse { false }
    }

    @Serializable
    data class InsertUserPayload(
        val user_id: String,
        @SerialName("clerk_user_id") val firebaseUid: String,
        val first_name: String,
        val last_name: String,
        val title: String,
        val phone: String,
        val email: String,
        val password: String,
        @SerialName("service_provider") val service_provider: Boolean = false
    )

    @Serializable
    data class DbService(
        val id: Int,
        val name: String,
        @SerialName("service_pic") val service_pic: String? = null,
        val description: String? = null,
        @SerialName("min_price") val min_price: Double? = null
    )

    @Serializable
    data class DbUser(
        val id: Int,
        @SerialName("user_id") val user_id: String? = null,
        /** Firebase UID; DB column is legacy-named `clerk_user_id`. */
        @SerialName("clerk_user_id") val firebaseUid: String? = null,
        @SerialName("first_name") val first_name: String = "",
        @SerialName("last_name") val last_name: String = "",
        @SerialName("title") val title: String? = null,
        @SerialName("phone") val phone: String? = null,
        @SerialName("email") val email: String? = null,
        @SerialName("prof_pic") val prof_pic: String? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("county") val county: String? = null,
        @SerialName("area_name") val area_name: String? = null,
        @SerialName("occupation") val occupation: String? = null,
        @SerialName("nat_id") val nat_id: String? = null,
        @SerialName("birth_date") val birth_date: String? = null,
        @SerialName("gender") val gender: String? = null,
        /** DB may return null for legacy or unset rows; treat as false in app logic. */
        @SerialName("service_provider") val service_provider: Boolean? = null,
        @SerialName("provider_application_pending") val provider_application_pending: Boolean? = null,
    )

    @Serializable
    data class DbSubscriptionPlan(
        val id: Int,
        val name: String,
        val description: String? = null,
        val price: Double,
        val currency: String = "KES",
        @SerialName("duration_type") val durationType: String = "month",
        @SerialName("duration_value") val durationValue: Int = 1,
        val features: List<String> = emptyList(),
        @SerialName("is_popular") val isPopular: Boolean = false
    )

    /**
     * Fetches active subscription plans from Supabase.
     * Public read (anon_select_active_plans RLS). Falls back to default plans when not configured or empty.
     */
    suspend fun getSubscriptionPlans(): List<SubscriptionPackage> {
        if (!isConfigured()) return getDefaultSubscriptionPlans()
        val client = SupabaseClientProvider.publicClient
        val rows = runCatching {
            client.from("subscription_plans").select() {
                filter { eq("is_active", true) }
                order(column = "sort_order", order = Order.ASCENDING)
            }.decodeList<DbSubscriptionPlan>()
        }.onFailure { e -> Log.w("SupabaseData", "getSubscriptionPlans failed, using defaults: ${e.message}") }
            .getOrElse { emptyList() }
        if (rows.isNotEmpty()) {
            return rows.map { mapDbPlanToSubscriptionPackage(it) }
        }
        return getDefaultSubscriptionPlans()
    }

    private fun mapDbPlanToSubscriptionPackage(db: DbSubscriptionPlan): SubscriptionPackage {
        val durationStr = when (db.durationType) {
            "year" -> if (db.durationValue == 1) "1 year" else "${db.durationValue} years"
            else -> if (db.durationValue == 1) "1 month" else "${db.durationValue} months"
        }
        val priceStr = if (db.price == db.price.toLong().toDouble()) "%,d".format(db.price.toLong()) else "%.2f".format(db.price)
        return SubscriptionPackage(
            id = db.id.toString(),
            name = db.name,
            description = db.description ?: "",
            price = priceStr,
            duration = durationStr,
            features = db.features,
            isPopular = db.isPopular
        )
    }

    /** Default plans when Supabase is not configured or table is empty. Align with Admin Portal seed. */
    private fun getDefaultSubscriptionPlans(): List<SubscriptionPackage> = listOf(
        SubscriptionPackage("1", "Basic", "Essential access to connect with caregivers and basic support.",
            "499", "1 month", listOf("Up to 5 consultations per month", "Chat with verified caregivers", "Basic helpline access"), false),
        SubscriptionPackage("2", "Premium", "Full access to all features, priority support, and exclusive content.",
            "999", "1 month", listOf("Unlimited consultations", "24/7 priority support", "Discount on booked services", "Early access to new features"), true),
        SubscriptionPackage("3", "Yearly", "Best value: pay annually and save. All Premium benefits.",
            "9,999", "1 year", listOf("Everything in Premium", "2 months free (save 17%)", "Unlimited consultations", "24/7 priority support"), false)
    )

    /**
     * Fetches all services. Public read, no auth required.
     * Uses fallback default services when Supabase is not configured or table is empty (populate via Admin Portal).
     */
    suspend fun getServices(): List<Service> {
        if (!isConfigured()) return getDefaultServices()
        val client = SupabaseClientProvider.publicClient
        val rows = runCatching {
            client.from("services").select().decodeList<DbService>()
        }.onFailure { e -> Log.w("SupabaseData", "getServices failed, using defaults: ${e.message}") }
            .getOrElse { emptyList() }
        if (rows.isNotEmpty()) {
            return rows.map { s ->
                Service(
                    service_id = s.id.toString(),
                    name = s.name,
                    pic = s.service_pic ?: "",
                    description = s.description ?: "",
                    min_price = s.min_price
                )
            }
        }
        return getDefaultServices()
    }

    /**
     * Local catalog when the network fails and app cache is empty — same rows as an empty `services` table.
     * Home screen uses at most 3 of these for showcase; full list remains in [getServices].
     */
    fun localFallbackServices(): List<Service> = getDefaultServices()

    /** Default services when table is empty. Align with Admin Portal / SUBSCRIPTION_ADMIN_PLAN. */
    private fun getDefaultServices(): List<Service> = listOf(
        Service(service_id = "1", name = "Mama Fua", pic = ""),
        Service(service_id = "2", name = "Tailor", pic = ""),
        Service(service_id = "3", name = "Care Giver", pic = ""),
        Service(service_id = "4", name = "House Manager", pic = ""),
        Service(service_id = "5", name = "Errand Girl", pic = "")
    )

    /**
     * Loads [users] for [firebaseUid] (DB column `clerk_user_id`) into prefs + [CurrentUser].
     */
    suspend fun syncLocalProfileFromSupabase(firebaseUid: String, prefs: SharedPreferences) {
        if (!isConfigured() || firebaseUid.isBlank()) return
        if (!SupabaseClientProvider.ensureSupabaseSession()) return
        val profile = getUserProfile(firebaseUid) ?: return
        val fullName = "${profile.first_name} ${profile.last_name}".trim()
        val nameForPrefs = fullName.takeIf { it.isNotBlank() } ?: prefs.getString("user_full_name", null)
        val prevPic = prefs.getString("user_pic", null)
        val editor = prefs.edit()
            .putString("user_full_name", nameForPrefs)
            .putString("user_phone", profile.phoneNumber)
            .putString("user_pic", profile.pic)
            .putString("user_email", profile.email ?: prefs.getString("user_email", null))
            .putBoolean("isProvider", profile.isServiceProvider == true)
            .putBoolean("isProviderPending", profile.isProviderApplicationPending == true)
        if (profile.pic != null && profile.pic != prevPic) {
            editor.putLong("profile_pic_version", System.currentTimeMillis())
        }
        editor.apply()
        CurrentUser.setUser(profile)
    }

    /**
     * Fetches profile by Firebase UID (stored in `users.clerk_user_id`). Requires a valid bridge JWT.
     */
    suspend fun getUserProfile(firebaseUid: String): User? {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return null
        val client = SupabaseClientProvider.client
        return runCatching {
            val rows = client.from("users").select {
                filter {
                    eq("clerk_user_id", firebaseUid)
                }
            }.decodeList<DbUser>()
            rows.firstOrNull()?.let { mapDbUserToUser(it) }
        }.onFailure { e ->
            Log.e("SupabaseData", "getUserProfile failed for clerk_user_id=$firebaseUid", e)
        }.getOrNull()
    }

    @Serializable
    data class DbPlanSubRow(
        val id: Int? = null,
        @SerialName("plan_id") val plan_id: Int = 0,
        val status: String = "",
        @SerialName("payment_reference") val payment_reference: String? = null,
        @SerialName("started_at") val started_at: String? = null,
        @SerialName("expires_at") val expires_at: String? = null,
        @SerialName("connects_granted") val connects_granted: Int? = null,
        @SerialName("connects_used") val connects_used: Int? = null,
    )

    data class ActiveSubscription(
        val planId: Int,
        val planName: String,
        val expiresAt: String?,
        /** When non-null, subscription bookings draw from this capped pool. Null means uncapped for this row. */
        val connectsGranted: Int? = null,
        val connectsUsed: Int? = null,
    )

    /**
     * True if the current user has an active subscription row for [planId] and [paymentReference].
     * Requires RLS `user_plan_subscriptions_select_own` (see backend/sql/supabase_user_plan_subscriptions_select_policy.sql).
     */
    suspend fun hasActiveSubscriptionForPayment(planId: Int, paymentReference: String): Boolean {
        if (!isConfigured() || planId < 1 || paymentReference.isBlank()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            val rows = SupabaseClientProvider.client.from("user_plan_subscriptions").select {
                filter {
                    eq("plan_id", planId)
                    eq("status", "active")
                    eq("payment_reference", paymentReference)
                }
            }.decodeList<DbPlanSubRow>()
            rows.isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Returns the user's active subscription (if any) with the plan name resolved from [subscription_plans].
     */
    suspend fun getActiveSubscription(): ActiveSubscription? {
        if (!isConfigured()) return null
        if (!SupabaseClientProvider.ensureSupabaseSession()) return null
        return runCatching {
            val rows = SupabaseClientProvider.client.from("user_plan_subscriptions").select {
                filter { eq("status", "active") }
                order(column = "expires_at", order = Order.DESCENDING)
                limit(1)
            }.decodeList<DbPlanSubRow>()
            val active = rows.firstOrNull() ?: return@runCatching null
            val plans = getSubscriptionPlans()
            val planName = plans.firstOrNull { it.id == active.plan_id.toString() }?.name ?: "Plan #${active.plan_id}"
            ActiveSubscription(
                planId = active.plan_id,
                planName = planName,
                expiresAt = active.expires_at,
                connectsGranted = active.connects_granted,
                connectsUsed = active.connects_used,
            )
        }.onFailure { e ->
            Log.e("SupabaseData", "getActiveSubscription failed", e)
        }.getOrNull()
    }

    @Serializable
    data class MyProviderProfileRow(
        val first_name: String? = null,
        val last_name: String? = null,
        val phone: String? = null,
        val email: String? = null,
        val occupation: String? = null,
        val title: String? = null,
        val working_hours: String? = null,
        val available_for_booking: Boolean? = null,
    )

    /**
     * Loads the current provider's profile fields needed by `update_my_profile`.
     * Requires a valid authenticated Supabase session (JWT).
     */
    suspend fun getMyProviderProfile(): MyProviderProfileRow? {
        if (!isConfigured()) return null
        if (!SupabaseClientProvider.ensureSupabaseSession()) return null
        val client = SupabaseClientProvider.client

        val uidRef = client.postgrest.rpc("get_my_user_id").decodeList<UidRow>().firstOrNull()?.uid ?: return null

        return runCatching {
            client.from("users").select {
                filter { eq("user_id", uidRef) }
            }.decodeList<MyProviderProfileRow>().firstOrNull()
        }.getOrNull()
    }

    /**
     * Updates provider headline/experience/working hours/availability in Supabase.
     * Uses `update_my_profile`, keeping untouched fields from the current profile row.
     */
    suspend fun updateMyProviderProfile(
        headline: String,
        experience: String,
        workingHours: String,
        availableForBooking: Boolean,
    ): Boolean {
        if (!isConfigured()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false

        val current = getMyProviderProfile() ?: return false
        return runCatching {
            val client = SupabaseClientProvider.client
            client.postgrest.rpc(
                "update_my_profile",
                buildJsonObject {
                    put("p_first_name", current.first_name ?: "")
                    put("p_last_name", current.last_name ?: "")
                    put("p_phone", current.phone ?: "")
                    put("p_email", current.email ?: "")
                    put("p_occupation", experience)
                    put("p_title", headline)
                    put("p_working_hours", workingHours)
                    put("p_available_for_booking", availableForBooking)
                },
            )
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "updateMyProviderProfile failed", e)
            false
        }
    }

    /**
     * Inserts a new user during onboarding. Requires a bridge JWT for RLS.
     * When Supabase is not configured (placeholder URL), returns without inserting (allows local-only onboarding).
     */
    suspend fun insertUser(payload: InsertUserPayload) {
        if (!isConfigured()) return  // Skip when Supabase not set up - complete onboarding locally
        if (!SupabaseClientProvider.ensureSupabaseSession()) {
            throw IllegalStateException("Supabase session not available. Sign in with Firebase and run auth-bridge.")
        }
        val client = SupabaseClientProvider.client
        client.from("users").insert(payload)
    }

    /**
     * Submits provider application: inserts into provider_applications and updates user.
     * Requires a valid Supabase (bridge) JWT. Admin approves from provider_applications.
     * @param serviceIds list of service IDs the user wants to offer (e.g. [1, 2, 3])
     */
    suspend fun submitProviderApplication(
        gender: String?,
        birthDate: String?,
        country: String?,
        county: String?,
        areaName: String?,
        natId: String?,
        emmCont1: String?,
        emmCont2: String?,
        serviceIds: List<Int>? = null,
        workingHours: String? = null,
        professionalTitle: String? = null,
        experience: String? = null,
    ): Boolean {
        if (!isConfigured()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        val client = SupabaseClientProvider.client
        val serviceIdsJson = serviceIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            ids.joinToString(prefix = "[", postfix = "]", transform = { it.toString() })
        } ?: ""
        val richParams = buildJsonObject {
            put("p_gender", gender ?: "")
            put("p_birth_date", birthDate ?: "")
            put("p_country", country ?: "")
            put("p_county", county ?: "")
            put("p_area_name", areaName ?: "")
            put("p_nat_id", natId ?: "")
            put("p_emm_cont_1", emmCont1 ?: "")
            put("p_emm_cont_2", emmCont2 ?: "")
            put("p_service_ids", serviceIdsJson)
            put("p_working_hours", workingHours ?: "")
            put("p_professional_title", professionalTitle ?: "")
            put("p_experience", experience ?: "")
        }
        // Try newer RPC signature first; fallback to older signature for compatibility.
        val richAttempt = runCatching {
            client.postgrest.rpc("submit_provider_application", richParams)
            true
        }.getOrNull()
        if (richAttempt == true) return true

        val legacyParams = buildJsonObject {
            put("p_gender", gender ?: "")
            put("p_birth_date", birthDate ?: "")
            put("p_country", country ?: "")
            put("p_county", county ?: "")
            put("p_area_name", areaName ?: "")
            put("p_nat_id", natId ?: "")
            put("p_emm_cont_1", emmCont1 ?: "")
            put("p_emm_cont_2", emmCont2 ?: "")
            put("p_service_ids", serviceIdsJson)
        }
        return runCatching {
            client.postgrest.rpc("submit_provider_application", legacyParams)
            true
        }.onFailure { e ->
            android.util.Log.e("SupabaseData", "submitProviderApplication failed", e)
        }.getOrDefault(false)
    }

    /**
     * Upserts live_location for the current user. Creates row if missing.
     * Returns true on success; on JWT expired, clears cache and retries once.
     */
    suspend fun upsertLiveLocation(latitude: Double, longitude: Double): Boolean {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        val client = SupabaseClientProvider.client
        val params = buildJsonObject { put("p_lat", latitude); put("p_lon", longitude) }
        val first = runCatching {
            client.postgrest.rpc("upsert_live_location", params)
            true
        }
        if (first.isSuccess) return true
        val ex = first.exceptionOrNull() ?: return false
        if (!isJwtExpiredError(ex)) {
            Log.e("SupabaseData", "upsertLiveLocation failed", ex)
            return false
        }
        // Retry once with fresh token
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            client.postgrest.rpc("upsert_live_location", params)
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "upsertLiveLocation retry failed", e)
            false
        }
    }

    /**
     * Updates current user's profile (first_name, last_name, phone, email, occupation, optional title).
     * Hosted DB may use the 8-arg `update_my_profile` (title, working_hours, available_for_booking); only
     * send keys that should change — omitting `p_working_hours` / `p_available_for_booking` leaves them unchanged.
     */
    suspend fun updateUserProfile(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        occupation: String,
        title: String? = null,
    ): Boolean = updateUserProfileResult(firstName, lastName, phone, email, occupation, title) == null

    private fun normalizeProfileEmail(value: String?): String {
        val s = value?.trim()?.lowercase().orEmpty()
        if (s.isEmpty() || s == "no email") return ""
        return s
    }

    /**
     * @param accountEmailHint Firebase / prefs email for this account. When the DB row has no email (or the
     * select fails) but the form still shows the same address, we omit [p_email] so Postgres does not run a
     * no-op email write that can hit [users_email_lower_unique] against a duplicate legacy row.
     *
     * @return null on success, or a short technical/detail string for logging; use [profileUpdateUserMessage] for UI.
     */
    suspend fun updateUserProfileResult(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        occupation: String,
        title: String? = null,
        accountEmailHint: String? = null,
    ): String? {
        if (!isConfigured()) return "supabase_not_configured"
        if (!SupabaseClientProvider.ensureSupabaseSession()) return "no_session"
        val titleTrimmed = title?.trim()?.takeIf { it.isNotEmpty() }
        val profile = runCatching { getMyProviderProfile() }.getOrNull()
        val emailNorm = normalizeProfileEmail(email)
        val dbNorm = normalizeProfileEmail(profile?.email)
        val hintNorm = normalizeProfileEmail(accountEmailHint)
        val emailUnchanged = when {
            dbNorm.isNotEmpty() && emailNorm == dbNorm -> true
            dbNorm.isEmpty() && hintNorm.isNotEmpty() && emailNorm == hintNorm -> true
            else -> false
        }
        val shouldSendEmailUpdate = emailNorm.isNotEmpty() && !emailUnchanged
        if (BuildConfig.DEBUG && !shouldSendEmailUpdate && emailNorm.isNotEmpty()) {
            Log.d("SupabaseData", "updateUserProfile: omitting p_email (unchanged vs db or account hint)")
        }
        return runCatching {
            val client = SupabaseClientProvider.client
            client.postgrest.rpc(
                "update_my_profile",
                buildJsonObject {
                    put("p_first_name", firstName)
                    put("p_last_name", lastName)
                    put("p_phone", phone)
                    if (shouldSendEmailUpdate) put("p_email", email.trim())
                    put("p_occupation", occupation)
                    titleTrimmed?.let { put("p_title", it) }
                },
            )
            null
        }.getOrElse { e ->
            Log.e("SupabaseData", "updateUserProfile failed", e)
            e.message ?: e::class.simpleName ?: "error"
        }
    }

    fun profileUpdateUserMessage(context: Context, detail: String?): String {
        val m = (detail ?: "").lowercase()
        val emailTaken = m.contains("users_email_lower_unique") ||
            m.contains("email_lower_unique") ||
            (m.contains("duplicate key") && (m.contains("users") && m.contains("email")))
        return when {
            emailTaken -> context.getString(R.string.settings_update_email_taken)
            m.contains("jwt") || m.contains("401") || m == "no_session" ->
                context.getString(R.string.settings_update_session)
            else -> context.getString(R.string.settings_update_failed)
        }
    }

    /**
     * Pushes registration form fields to `users` after auth-bridge creates the row (bridge only sets placeholders).
     */
    suspend fun applyRegistrationProfile(
        firstName: String,
        lastName: String,
        phone: String,
        title: String,
        email: String,
    ): Boolean {
        if (!isConfigured()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            SupabaseClientProvider.client.postgrest.rpc(
                "update_my_profile",
                buildJsonObject {
                    put("p_first_name", firstName)
                    put("p_last_name", lastName)
                    put("p_phone", phone)
                    put("p_email", email)
                    put("p_occupation", "")
                    put("p_title", title)
                },
            )
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "applyRegistrationProfile failed", e)
            false
        }
    }

    /**
     * Inserts a problem report for the current user.
     * Requires supabase_profile_and_reports.sql (problem_reports table + insert_my_problem_report RPC) to be run in Supabase.
     */
    suspend fun reportProblem(description: String): Boolean {
        if (!isConfigured() || description.isBlank()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            val client = SupabaseClientProvider.client
            client.postgrest.rpc("insert_my_problem_report", buildJsonObject { put("p_description", description) })
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "reportProblem failed", e)
            false
        }
    }

    suspend fun reportGbvEmergency(latitude: Double?, longitude: Double?, locationText: String?): Boolean {
        if (!isConfigured()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            SupabaseClientProvider.client.postgrest.rpc(
                "insert_my_help_request",
                buildJsonObject {
                    latitude?.let { put("p_latitude", it) }
                    longitude?.let { put("p_longitude", it) }
                    locationText?.let { put("p_location_text", it) }
                },
            )
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "reportGbvEmergency failed", e)
            false
        }
    }

    data class SubmitJobReviewResult(val ok: Boolean, val err: String? = null)

    /**
     * Submits stars + optional text for a completed job (RPC `submit_job_review`).
     * Seeker → provider review is public; provider → seeker review is private to the seeker.
     */
    suspend fun submitJobReview(jobId: Int, stars: Int, reviewText: String?): SubmitJobReviewResult {
        if (!isConfigured()) return SubmitJobReviewResult(false, "supabase_not_configured")
        if (!SupabaseClientProvider.ensureSupabaseSession()) return SubmitJobReviewResult(false, "auth_required")
        @Serializable
        data class RpcRow(val ok: Boolean = false, val err: String? = null)
        return runCatching {
            val params = buildJsonObject {
                put("p_job_id", jobId)
                put("p_stars", stars)
                if (!reviewText.isNullOrBlank()) put("p_review_text", reviewText.trim())
            }
            val rows = SupabaseClientProvider.client.postgrest.rpc("submit_job_review", params).decodeList<RpcRow>()
            val row = rows.firstOrNull()
            when {
                row?.ok == true -> SubmitJobReviewResult(true, null)
                !row?.err.isNullOrBlank() -> SubmitJobReviewResult(false, row.err)
                else -> SubmitJobReviewResult(false, "unknown")
            }
        }.getOrElse { e ->
            Log.e("SupabaseData", "submitJobReview failed", e)
            SubmitJobReviewResult(false, "request_failed")
        }
    }

    data class PublicReviewItem(
        val stars: Int,
        val reviewText: String,
        val serviceName: String,
        val createdAt: String,
        val reviewerFirstName: String,
    )

    @Serializable
    private data class PublicRatingStatsRpcRow(
        @SerialName("avg_stars") val avg_stars: Double = 0.0,
        @SerialName("review_count") val review_count: Int = 0,
    )

    @Serializable
    private data class PublicReviewRpcRow(
        val stars: Int = 0,
        val review_text: String? = null,
        val service_name: String? = null,
        val created_at: String? = null,
        val reviewer_first_name: String? = null,
    )

    suspend fun getPublicRatingStatsForUser(userDbId: Int): Pair<Double, Int>? {
        if (!isConfigured() || userDbId < 1) return null
        return runCatching {
            val client = SupabaseClientProvider.publicClient
            val rows = client.postgrest.rpc(
                "get_public_rating_stats_for_user",
                buildJsonObject { put("p_user_id", userDbId) },
            ).decodeList<PublicRatingStatsRpcRow>()
            val r = rows.firstOrNull() ?: return@runCatching null
            r.avg_stars to r.review_count
        }.onFailure { e ->
            Log.w("SupabaseData", "getPublicRatingStatsForUser failed", e)
        }.getOrNull()
    }

    suspend fun listPublicReviewsForUser(userDbId: Int, limit: Int = 25): List<PublicReviewItem> {
        if (!isConfigured() || userDbId < 1) return emptyList()
        return runCatching {
            val client = SupabaseClientProvider.publicClient
            val rows = client.postgrest.rpc(
                "list_public_reviews_for_user",
                buildJsonObject {
                    put("p_user_id", userDbId)
                    put("p_limit", limit)
                },
            ).decodeList<PublicReviewRpcRow>()
            rows.map {
                PublicReviewItem(
                    stars = it.stars,
                    reviewText = it.review_text?.trim().orEmpty(),
                    serviceName = it.service_name?.trim().orEmpty(),
                    createdAt = it.created_at?.trim().orEmpty(),
                    reviewerFirstName = it.reviewer_first_name?.trim().orEmpty(),
                )
            }
        }.onFailure { e ->
            Log.w("SupabaseData", "listPublicReviewsForUser failed", e)
        }.getOrElse { emptyList() }
    }

    /**
     * Inserts a help request (GBV hotline / Get Help button).
     * Requires supabase_profile_and_reports.sql (help_requests table + insert_my_help_request RPC) to be run in Supabase.
     */
    suspend fun insertHelpRequest(): Boolean {
        if (!isConfigured()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            val client = SupabaseClientProvider.client
            client.postgrest.rpc("insert_my_help_request")
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "insertHelpRequest failed", e)
            false
        }
    }

    /**
     * Returns booking requests for the current user (client/provider).
     * Uses Supabase RPC `get_my_booking_requests()`.
     */
    suspend fun getMyBookingRequests(): List<MyBookingRequest> {
        if (!isConfigured()) return emptyList()
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()

        return runCatching {
            val client = SupabaseClientProvider.client
            runCatching {
                client.postgrest.rpc("get_my_booking_requests").decodeList<MyBookingRequest>()
            }.recoverCatching { e ->
                Log.w("SupabaseData", "get_my_booking_requests flat decode failed, trying envelope: ${e.message}")
                client.postgrest.rpc("get_my_booking_requests")
                    .decodeList<MyBookingRequestRpcEnvelope>()
                    .mapNotNull { it.get_my_booking_requests }
            }.getOrThrow()
        }.getOrElse { e ->
            Log.e("SupabaseData", "getMyBookingRequests failed", e)
            emptyList()
        }
    }

    suspend fun acceptBookingRequest(requestId: Long): BookingActionOutcome {
        if (!isConfigured()) return BookingActionOutcome(isSuccess = false, errorCode = "supabase_not_configured")
        if (!SupabaseClientProvider.ensureSupabaseSession()) return BookingActionOutcome(isSuccess = false, errorCode = "auth_required")

        @Serializable
        data class AcceptBookingRpcRow(
            @SerialName("quote_id") val quote_id: String? = null,
            @SerialName("chat_code") val chat_code: String? = null,
            @SerialName("job_id") val job_id: String? = null,
            @SerialName("err") val err: String? = null,
        )

        return runCatching {
            val rows = SupabaseClientProvider.client.postgrest.rpc(
                "accept_booking_request",
                buildJsonObject { put("p_request_id", requestId) },
            ).decodeList<AcceptBookingRpcRow>()

            val row = rows.firstOrNull()
            if (row == null) return@runCatching BookingActionOutcome(false, "request_failed")
            if (row.err.isNullOrBlank()) {
                BookingActionOutcome(
                    isSuccess = true,
                    chatCode = row.chat_code,
                    quoteId = row.quote_id,
                )
            } else {
                BookingActionOutcome(isSuccess = false, errorCode = row.err)
            }
        }.getOrElse { e ->
            Log.e("SupabaseData", "acceptBookingRequest failed", e)
            BookingActionOutcome(isSuccess = false, errorCode = mapBookingErrorCode(e))
        }
    }

    suspend fun declineBookingRequest(requestId: Long): BookingActionOutcome {
        if (!isConfigured()) return BookingActionOutcome(isSuccess = false, errorCode = "supabase_not_configured")
        if (!SupabaseClientProvider.ensureSupabaseSession()) return BookingActionOutcome(isSuccess = false, errorCode = "auth_required")

        @Serializable
        data class ErrRow(@SerialName("err") val err: String? = null)

        return runCatching {
            val rows = SupabaseClientProvider.client.postgrest.rpc(
                "decline_booking_request",
                buildJsonObject { put("p_request_id", requestId) },
            ).decodeList<ErrRow>()

            val row = rows.firstOrNull()
            if (row == null) BookingActionOutcome(false, "request_failed")
            else if (row.err.isNullOrBlank()) BookingActionOutcome(true)
            else BookingActionOutcome(false, row.err)
        }.getOrElse { e ->
            Log.e("SupabaseData", "declineBookingRequest failed", e)
            BookingActionOutcome(false, mapBookingErrorCode(e))
        }
    }

    suspend fun cancelBookingRequest(requestId: Long): BookingActionOutcome {
        if (!isConfigured()) return BookingActionOutcome(isSuccess = false, errorCode = "supabase_not_configured")
        if (!SupabaseClientProvider.ensureSupabaseSession()) return BookingActionOutcome(isSuccess = false, errorCode = "auth_required")

        @Serializable
        data class ErrRow(@SerialName("err") val err: String? = null)

        return runCatching {
            val rows = SupabaseClientProvider.client.postgrest.rpc(
                "cancel_booking_request",
                buildJsonObject { put("p_request_id", requestId) },
            ).decodeList<ErrRow>()

            val row = rows.firstOrNull()
            if (row == null) BookingActionOutcome(false, "request_failed")
            else if (row.err.isNullOrBlank()) BookingActionOutcome(true)
            else BookingActionOutcome(false, row.err)
        }.getOrElse { e ->
            Log.e("SupabaseData", "cancelBookingRequest failed", e)
            BookingActionOutcome(false, mapBookingErrorCode(e))
        }
    }

    @Serializable
    data class JobRpcRow(
        val client: String = "",
        val provider: String = "",
        @SerialName("Service") val service: String = "",
        @SerialName("Price") val price: Double = 0.0,
        val location: String? = null,
        @SerialName("job_id") val job_id: Int = 0,
        val rated: Boolean = false,
        val score: Float = 0f,
        @SerialName("my_review_submitted") val my_review_submitted: Boolean? = null,
        @SerialName("my_stars") val my_stars: Float? = null,
        @SerialName("their_review_submitted") val their_review_submitted: Boolean? = null,
        @SerialName("their_stars") val their_stars: Float? = null,
        @SerialName("started_at") val started_at: String? = null,
        @SerialName("completed_at") val completed_at: String? = null,
    )

    private fun JobRpcRow.toJob(): Job {
        val mySubmitted = my_review_submitted ?: rated
        val myStarVal = my_stars ?: score
        return Job(
            client = client,
            provider = provider,
            service = service,
            price = price,
            location = location ?: "",
            job_id = job_id,
            rated = mySubmitted,
            score = myStarVal,
            my_review_submitted = mySubmitted,
            my_stars = myStarVal,
            their_review_submitted = their_review_submitted ?: false,
            their_stars = their_stars ?: 0f,
            started_at = started_at,
            completed_at = completed_at,
        )
    }

    /**
     * Fetches pending jobs for the current user (client or provider).
     * Requires get_pending_jobs RPC in Supabase.
     */
    suspend fun getPendingJobs(): List<Job> {
        if (!isConfigured()) return emptyList()
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()
        return runCatching {
            val rows = SupabaseClientProvider.client.postgrest.rpc("get_pending_jobs").decodeList<JobRpcRow>()
            rows.map { it.toJob() }
        }.getOrElse { e ->
            Log.e("SupabaseData", "getPendingJobs failed", e)
            emptyList()
        }
    }

    /**
     * Fetches completed jobs for the current user (client or provider).
     * Requires get_completed_jobs RPC in Supabase.
     */
    suspend fun getCompletedJobs(): List<Job> {
        if (!isConfigured()) return emptyList()
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()
        return runCatching {
            val rows = SupabaseClientProvider.client.postgrest.rpc("get_completed_jobs").decodeList<JobRpcRow>()
            rows.map { it.toJob() }
        }.getOrElse { e ->
            Log.e("SupabaseData", "getCompletedJobs failed", e)
            emptyList()
        }
    }

    /**
     * Marks a job as complete. Returns true on success.
     */
    suspend fun completeJob(jobId: Int): Boolean {
        if (!isConfigured()) return false
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            SupabaseClientProvider.client.postgrest.rpc("complete_my_job", buildJsonObject { put("p_job_id", jobId) })
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "completeJob failed", e)
            false
        }
    }

    @Serializable
    private data class ProviderBookableRpcRow(val bookable: Boolean = false)

    /** Matches marketplace: service provider, available_for_booking, and no incomplete job. */
    suspend fun providerIsBookable(userDbId: Int): Boolean {
        if (!isConfigured() || userDbId < 1) return false
        return runCatching {
            val rows = SupabaseClientProvider.publicClient.postgrest.rpc(
                "provider_is_bookable",
                buildJsonObject { put("p_user_id", userDbId) },
            ).decodeList<ProviderBookableRpcRow>()
            rows.firstOrNull()?.bookable == true
        }.onFailure { e ->
            Log.w("SupabaseData", "providerIsBookable failed", e)
        }.getOrDefault(false)
    }

    @Serializable
    private data class ProviderBusyRpcRow(val busy: Boolean = false)

    /** True when the signed-in user is a provider with at least one incomplete job. */
    suspend fun myProviderHasActiveJob(): Boolean {
        if (!isConfigured() || !SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            SupabaseClientProvider.client.postgrest.rpc("my_provider_has_active_job")
                .decodeList<ProviderBusyRpcRow>()
                .firstOrNull()?.busy == true
        }.onFailure { e ->
            Log.w("SupabaseData", "myProviderHasActiveJob failed", e)
        }.getOrDefault(false)
    }

    /**
     * Fetches providers for a service (subscribed + service_provider). Uses RPC.
     */
    /**
     * Providers for a service. When [seekerLat] and [seekerLng] are non-null, uses PostGIS RPC
     * [get_providers_for_service_near] (default 10 km). Otherwise lists all providers for the service.
     */
    /**
     * Marketplace provider lists: hide the signed-in user so providers never see themselves.
     */
    private suspend fun excludeCurrentUserFromProviderList(users: List<User>): List<User> {
        if (users.isEmpty()) return users
        val me = CurrentUser.getUser()
        val meDb = me?.userDbId
        val idCandidates = buildSet {
            me?.id?.takeIf { it.isNotBlank() }?.let(::add)
            me?.user_name?.takeIf { it.isNotBlank() }?.let(::add)
        }
        val extras = if (meDb == null && idCandidates.isEmpty()) {
            if (SupabaseClientProvider.ensureSupabaseSession()) {
                getMyUserId()?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
            } else {
                emptySet()
            }
        } else {
            emptySet()
        }
        val allIds = idCandidates + extras
        if (meDb == null && allIds.isEmpty()) return users
        return users.filter { u ->
            val sameDb = meDb != null && u.userDbId != null && u.userDbId == meDb
            val sameId = allIds.any { id -> u.id == id || u.user_name == id }
            !sameDb && !sameId
        }
    }

    suspend fun getProvidersForService(
        serviceId: String,
        seekerLat: Double? = null,
        seekerLng: Double? = null,
        radiusMeters: Double = 10_000.0,
        excludeCurrentUser: Boolean = true,
    ): List<User> {
        if (!isConfigured()) return emptyList()
        val client = SupabaseClientProvider.publicClient
        val sid = serviceId.toIntOrNull() ?: return emptyList()
        val useNear = seekerLat != null && seekerLng != null
        val mapped = runCatching {
            val rows = if (useNear) {
                client.postgrest.rpc(
                    "get_providers_for_service_near",
                    buildJsonObject {
                        put("p_service_id", sid)
                        put("p_lat", seekerLat!!)
                        put("p_lng", seekerLng!!)
                        put("p_radius_meters", radiusMeters)
                    },
                ).decodeList<ProviderRpcRow>()
            } else {
                client.postgrest.rpc("get_providers_for_service", buildJsonObject { put("p_service_id", sid) })
                    .decodeList<ProviderRpcRow>()
            }
            rows.map { mapProviderToUser(it) }
        }.onFailure { e ->
            Log.e("SupabaseData", "getProvidersForService failed", e)
        }.getOrElse { emptyList() }
        return if (excludeCurrentUser) excludeCurrentUserFromProviderList(mapped) else mapped
    }

    @Serializable
    data class ProviderRpcRow(
        val id: Int? = null,
        @SerialName("user_name") val user_name: String? = null,
        @SerialName("first_name") val first_name: String? = null,
        @SerialName("last_name") val last_name: String? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("phone") val phone: String? = null,
        @SerialName("pic") val pic: String? = null,
        @SerialName("area_name") val area_name: String? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("county") val county: String? = null,
        val nat_id: String? = null,
        val dob: String? = null,
        val gender: String? = null,
        /** Experience / bio text from provider profile (see `get_providers_for_service`). */
        val occupation: String? = null,
        @SerialName("working_hours") val working_hours: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        @SerialName("WH Badge") val wh_badge: String? = null,
    )

    data class StartConversationOutcome(
        val chatCode: String? = null,
        val quoteId: String? = null,
        val errorCode: String? = null,
    )

    @Serializable
    private data class StartConversationRpcRow(
        @SerialName("quote_id") val quote_id: String? = null,
        @SerialName("chat_code") val chat_code: String? = null,
        val err: String? = null,
    )

    /**
     * Opens or resumes a chat with a provider for the given service (RPC `start_conversation_with_provider`).
     */
    suspend fun startConversationWithProvider(providerRef: String, serviceId: Int): StartConversationOutcome {
        if (!isConfigured()) return StartConversationOutcome(errorCode = "supabase_not_configured")
        if (!SupabaseClientProvider.ensureSupabaseSession()) return StartConversationOutcome(errorCode = "auth_required")
        return runCatching {
            val rows = SupabaseClientProvider.client.postgrest.rpc(
                "start_conversation_with_provider",
                buildJsonObject {
                    put("p_provider_ref", providerRef)
                    put("p_service_id", serviceId)
                },
            ).decodeList<StartConversationRpcRow>()
            val r = rows.firstOrNull()
            StartConversationOutcome(
                chatCode = r?.chat_code,
                quoteId = r?.quote_id,
                errorCode = r?.err,
            )
        }.getOrElse { e ->
            Log.e("SupabaseData", "startConversationWithProvider failed", e)
            val code = if (e is HttpRequestTimeoutException) "network_timeout" else "request_failed"
            StartConversationOutcome(errorCode = code)
        }
    }

    /**
     * Fetches conversations (quotes with last message preview).
     */
    suspend fun getConversations(): List<Conversation> {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()
        val client = SupabaseClientProvider.client
        return runCatching {
            client.postgrest.rpc("get_conversations").decodeList<ConversationRow>()
                .map {
                    Conversation(
                        it.quote_code,
                        it.chat_id,
                        it.provider,
                        it.client,
                        it.service,
                        it.msg_text,
                        it.msg_time,
                        peerName = it.peer_name?.trim().orEmpty(),
                        peerPic = it.peer_pic?.trim()?.takeIf { u -> u.isNotEmpty() },
                    )
                }
        }.getOrElse { emptyList() }
    }

    @Serializable
    data class ConversationRow(
        @SerialName("quote_code") val quote_code: String,
        @SerialName("chat_id") val chat_id: String,
        val provider: String,
        val client: String,
        val service: String,
        @SerialName("msg_text") val msg_text: String,
        @SerialName("msg_time") val msg_time: String,
        @SerialName("peer_name") val peer_name: String? = null,
        @SerialName("peer_pic") val peer_pic: String? = null,
    )

    @Serializable
    data class ChatHeaderRow(
        @SerialName("peer_name") val peer_name: String? = null,
        @SerialName("peer_pic") val peer_pic: String? = null,
        @SerialName("service_name") val service_name: String? = null,
    )

    suspend fun getChatHeader(chatCode: String): ChatHeaderRow? {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return null
        if (chatCode.isBlank()) return null
        return runCatching {
            SupabaseClientProvider.client.postgrest.rpc(
                "get_chat_header",
                buildJsonObject { put("p_chat_code", chatCode) },
            ).decodeList<ChatHeaderRow>().firstOrNull()
        }.onFailure { e ->
            Log.e("SupabaseData", "getChatHeader failed", e)
        }.getOrNull()
    }

    /**
     * Fetches messages for a chat by chat_code.
     */
    suspend fun getChatMessages(chatCode: String): List<ChatMessage> {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()
        val client = SupabaseClientProvider.client
        val chatRows = client.from("chats").select() {
            filter { eq("chat_code", chatCode) }
        }.decodeList<DbChat>()
        val chatId = chatRows.firstOrNull()?.id ?: return emptyList()
        val msgRows = client.from("messages").select() {
            filter { eq("chat_id", chatId) }
            order(column = "time", order = Order.ASCENDING)
        }.decodeList<DbMessage>()
        return msgRows.map { m ->
            val ts = try { java.time.Instant.parse(m.time).toEpochMilli() } catch (_: Exception) { 0L }
            ChatMessage(
                id = m.id.toString(),
                sender_id = m.sender_id ?: "",
                receiverId = "",
                message = m.content ?: "",
                timestamp = ts,
                isRead = false
            )
        }
    }

    @Serializable
    data class UidRow(val uid: String? = null)

    @Serializable
    data class DbChat(val id: Int, @SerialName("chat_code") val chat_code: String? = null, @SerialName("quote_id") val quote_id: Int? = null)

    @Serializable
    data class DbMessage(val id: Int, @SerialName("sender_id") val sender_id: String? = null, val content: String? = null, val time: String? = null, @SerialName("chat_id") val chat_id: Int? = null)

    @Serializable
    private data class MessageInsertPayload(
        @SerialName("chat_id") val chat_id: Int,
        @SerialName("sender_id") val sender_id: String,
        val content: String,
    )

    @Serializable
    data class DbUserIdRow(val id: Int)

    @Serializable
    data class DbDocumentTypeRow(val id: Int, val name: String? = null)

    @Serializable
    private data class ProviderDocumentInsertPayload(
        @SerialName("user_id") val user_id: Int,
        @SerialName("doc_type_id") val doc_type_id: Int,
        val name: String,
        val verified: Boolean,
    )

    @Serializable
    private data class DbDocumentRow(
        val id: Int,
        val name: String? = null,
        @SerialName("doc_type_id") val doc_type_id: Int? = null,
        @SerialName("verified") val verified: Boolean? = false,
    )

    data class VerificationDocumentItem(
        val id: Int,
        val docTypeName: String,
        val verified: Boolean,
        val fileLabel: String,
        /** Signed download URL; private bucket objects are not viewable without this. */
        val signedUrl: String?,
    )

    /**
     * Lists verification documents for the current user (RLS). [signedUrl] is valid for a limited time.
     */
    suspend fun listMyVerificationDocuments(): List<VerificationDocumentItem> {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()
        val client = SupabaseClientProvider.client
        val typeNames: Map<Int, String?> = runCatching {
            client.from("document_type").select().decodeList<DbDocumentTypeRow>().associate { it.id to it.name }
        }.getOrElse { emptyMap() }
        val rows = runCatching {
            client.from("documents").select {
                order(column = "id", order = Order.DESCENDING)
            }.decodeList<DbDocumentRow>()
        }.onFailure { e ->
            Log.e("SupabaseData", "listMyVerificationDocuments select failed", e)
        }.getOrElse { emptyList() }
        val bucket = client.storage.from(PROVIDER_DOCS_BUCKET)
        return rows.mapNotNull { row ->
            val path = storagePathFromStoredDocName(row.name, PROVIDER_DOCS_BUCKET) ?: run {
                Log.w("SupabaseData", "listMyVerificationDocuments: could not parse storage path for doc id=${row.id}")
                return@mapNotNull null
            }
            val label = path.substringAfterLast('/').ifBlank { "document-${row.id}" }
            val rawLabel = row.doc_type_id?.let { typeNames[it] }
            val typeName = rawLabel?.takeIf { !it.isNullOrBlank() } ?: "Document #${row.doc_type_id ?: "?"}"
            val signed = runCatching {
                bucket.createSignedUrl(path, 3600.seconds)
            }.onFailure { e ->
                Log.w("SupabaseData", "createSignedUrl failed id=${row.id} path=$path", e)
            }.getOrNull()
            VerificationDocumentItem(
                id = row.id,
                docTypeName = typeName,
                verified = row.verified == true,
                fileLabel = label,
                signedUrl = signed,
            )
        }
    }

    /**
     * Portfolio / verification files for another provider (seeker view on profile).
     * Requires auth + [documents_seeker_read_portfolio] RLS and storage read policy on `provider-docs`.
     */
    suspend fun listProviderPortfolioDocuments(providerUserDbId: Int): List<VerificationDocumentItem> {
        if (!isConfigured() || providerUserDbId < 1) return emptyList()
        if (!SupabaseClientProvider.ensureSupabaseSession()) return emptyList()
        val client = SupabaseClientProvider.client
        val typeNames: Map<Int, String?> = runCatching {
            client.from("document_type").select().decodeList<DbDocumentTypeRow>().associate { it.id to it.name }
        }.getOrElse { emptyMap() }
        val rows = runCatching {
            client.from("documents").select {
                filter { eq("user_id", providerUserDbId) }
                order(column = "id", order = Order.DESCENDING)
            }.decodeList<DbDocumentRow>()
        }.onFailure { e ->
            Log.w("SupabaseData", "listProviderPortfolioDocuments select failed", e)
        }.getOrElse { emptyList() }
        val bucket = client.storage.from(PROVIDER_DOCS_BUCKET)
        return rows.mapNotNull { row ->
            val path = storagePathFromStoredDocName(row.name, PROVIDER_DOCS_BUCKET) ?: run {
                Log.w("SupabaseData", "listProviderPortfolioDocuments: bad path doc id=${row.id}")
                return@mapNotNull null
            }
            val label = path.substringAfterLast('/').ifBlank { "document-${row.id}" }
            val rawLabel = row.doc_type_id?.let { typeNames[it] }
            val typeName = rawLabel?.takeIf { !it.isNullOrBlank() } ?: "Document #${row.doc_type_id ?: "?"}"
            val signed = runCatching {
                bucket.createSignedUrl(path, 3600.seconds)
            }.onFailure { e ->
                Log.w("SupabaseData", "portfolio createSignedUrl failed id=${row.id}", e)
            }.getOrNull()
            VerificationDocumentItem(
                id = row.id,
                docTypeName = typeName,
                verified = row.verified == true,
                fileLabel = label,
                signedUrl = signed,
            )
        }
    }

    private fun storagePathFromStoredDocName(name: String?, bucketId: String): String? {
        if (name.isNullOrBlank()) return null
        val n = name.trim()
        if (!n.contains("://")) return n.trimStart('/')
        val markers = listOf(
            "/storage/v1/object/$bucketId/",
            "/storage/v1/object/public/$bucketId/",
        )
        for (m in markers) {
            val idx = n.indexOf(m, ignoreCase = true)
            if (idx >= 0) {
                return n.substring(idx + m.length).substringBefore('?').substringBefore('#')
            }
        }
        return null
    }

    /**
     * Sends a message to a chat. Requires chat_code.
     */
    suspend fun sendChatMessage(chatCode: String, content: String): Boolean {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        return runCatching {
            val client = SupabaseClientProvider.client
            val chatRows = client.from("chats").select() { filter { eq("chat_code", chatCode) } }.decodeList<DbChat>()
            val chatId = chatRows.firstOrNull()?.id ?: return@runCatching false
            val senderId =
                client.postgrest.rpc("get_my_user_id").decodeList<UidRow>().firstOrNull()?.uid ?: return@runCatching false
            client.from("messages").insert(
                MessageInsertPayload(chat_id = chatId, sender_id = senderId, content = content),
            )
            true
        }.getOrElse { e ->
            Log.e("SupabaseData", "sendChatMessage failed", e)
            false
        }
    }

    suspend fun getMyUserId(): String? {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return null
        return runCatching {
            SupabaseClientProvider.client.postgrest.rpc("get_my_user_id").decodeList<UidRow>().firstOrNull()?.uid
        }.getOrNull()
    }

    /**
     * Uploads profile pic to Supabase Storage, updates users.prof_pic via RPC.
     */
    suspend fun uploadProfilePic(fileBytes: ByteArray, fileName: String): String? {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return null
        val client = SupabaseClientProvider.client
        val userId = client.postgrest.rpc("get_my_user_id").decodeList<UidRow>().firstOrNull()?.uid
        if (userId.isNullOrBlank()) {
            Log.e("SupabaseData", "uploadProfilePic: get_my_user_id returned empty")
            return null
        }
        val safeName = fileName.trim().ifBlank { "profile.jpg" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val path = "profiles/$userId/$safeName"
        val bucket = client.storage.from(PROFILE_PHOTOS_BUCKET)
        runCatching {
            bucket.upload(path, fileBytes) {
                upsert = true
            }
        }.onFailure { e ->
            Log.e("SupabaseData", "uploadProfilePic storage upload failed", e)
            return null
        }
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val publicUrl = "$base/storage/v1/object/public/$PROFILE_PHOTOS_BUCKET/$path"
        runCatching {
            client.postgrest.rpc("update_my_prof_pic", buildJsonObject { put("p_url", publicUrl) })
                .decodeAs<Boolean>()
        }.onSuccess { rpcOk ->
            if (!rpcOk) {
                Log.w("SupabaseData", "uploadProfilePic: update_my_prof_pic reported false")
            }
        }.onFailure { e ->
            Log.w("SupabaseData", "uploadProfilePic update_my_prof_pic decode/call: ${e.message}")
        }
        val jwtSub = SupabaseTokenStore.getJwtOrNull()?.let { SupabaseJwtUtils.decodeJwtSub(it) }
        if (jwtSub.isNullOrBlank()) {
            Log.e("SupabaseData", "uploadProfilePic: no JWT sub; cannot verify prof_pic row")
            return null
        }
        val profileAfter = runCatching {
            client.from("users").select {
                filter { eq("clerk_user_id", jwtSub) }
            }.decodeList<DbUser>().firstOrNull()
        }.onFailure { e ->
            Log.e("SupabaseData", "uploadProfilePic: could not re-read users row", e)
        }.getOrNull()
        val savedUrl = profileAfter?.prof_pic?.trim()
        if (savedUrl.isNullOrBlank() || savedUrl != publicUrl.trim()) {
            Log.e(
                "SupabaseData",
                "uploadProfilePic: DB prof_pic mismatch after RPC (expected public URL prefix; got ${savedUrl?.take(96)})",
            )
            return null
        }
        return publicUrl
    }

    private const val PROVIDER_DOCS_BUCKET = "provider-docs"

    /**
     * Upload provider verification document and insert a documents row for admin review.
     */
    suspend fun uploadProviderVerificationDocument(
        fileBytes: ByteArray,
        fileName: String,
        docTypeLabel: String,
    ): Boolean {
        if (!SupabaseClientProvider.ensureSupabaseSession()) return false
        val client = SupabaseClientProvider.client
        val userRef = client.postgrest.rpc("get_my_user_id").decodeList<UidRow>().firstOrNull()?.uid
        if (userRef.isNullOrBlank()) {
            Log.e("SupabaseData", "uploadProviderVerificationDocument: get_my_user_id returned empty")
            return false
        }
        val internalUserId = client.from("users").select {
            filter { eq("user_id", userRef) }
        }.decodeList<DbUserIdRow>().firstOrNull()?.id
        if (internalUserId == null) {
            Log.e("SupabaseData", "uploadProviderVerificationDocument: could not resolve internal user id")
            return false
        }

        val normalized = docTypeLabel.trim().lowercase()
        val docTypes = client.from("document_type").select().decodeList<DbDocumentTypeRow>()
        val chosenType = docTypes.firstOrNull { row ->
            val n = row.name.orEmpty().lowercase()
            n.contains(normalized) || normalized.contains(n)
        } ?: docTypes.firstOrNull()
        val docTypeId = chosenType?.id
        if (docTypeId == null) {
            Log.e("SupabaseData", "uploadProviderVerificationDocument: no matching document_type for '$docTypeLabel'")
            return false
        }

        val path = "provider_docs/$userRef/${System.currentTimeMillis()}_$fileName"
        val bucket = client.storage.from(PROVIDER_DOCS_BUCKET)
        runCatching {
            bucket.upload(path, fileBytes) { upsert = true }
        }.onFailure { e ->
            Log.e("SupabaseData", "uploadProviderVerificationDocument storage upload failed", e)
            return false
        }

        val docUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/$PROVIDER_DOCS_BUCKET/$path"
        return runCatching {
            client.from("documents").insert(
                ProviderDocumentInsertPayload(
                    user_id = internalUserId,
                    doc_type_id = docTypeId,
                    name = docUrl,
                    verified = false,
                ),
            )
            true
        }.onFailure { e ->
            Log.e("SupabaseData", "uploadProviderVerificationDocument documents insert failed", e)
        }.getOrDefault(false)
    }

    private fun mapProviderToUser(p: ProviderRpcRow): User {
        val rawPic = p.pic
        val picUrl = when {
            rawPic.isNullOrBlank() -> null
            rawPic.startsWith("http", ignoreCase = true) -> rawPic
            else -> null
        }
        return User(
            id = p.user_name ?: p.id?.toString() ?: "",
            first_name = p.first_name ?: "",
            last_name = p.last_name ?: "",
            title = p.title,
            user_name = p.user_name ?: "",
            nat_id = p.nat_id,
            dob = p.dob,
            gender = p.gender,
            occupation = p.occupation,
            pic = picUrl,
            isIdVerified = null,
            isMobileVerified = null,
            isAvailable = true,
            details = p.occupation,
            phoneNumber = p.phone,
            country = p.country,
            county = p.county,
            area_name = p.area_name,
            email = null,
            userDbId = p.id,
            isServiceProvider = true,
            professionalTitle = p.title,
            workingHours = p.working_hours,
        )
    }

    suspend fun searchUsers(query: String): List<User> {
        if (!isConfigured() || query.isBlank()) return emptyList()
        val client = SupabaseClientProvider.publicClient
        val mapped = runCatching {
            val pattern = "%${query.trim()}%"
            val rows = client.postgrest.from("users").select {
                filter {
                    or {
                        ilike("first_name", pattern)
                        ilike("last_name", pattern)
                        ilike("occupation", pattern)
                        ilike("county", pattern)
                    }
                }
                limit(30)
            }.decodeList<DbUser>()
            rows.filter { it.service_provider == true }.map { mapDbUserToUser(it) }
        }.getOrElse {
            Log.w("SupabaseData", "searchUsers failed", it)
            emptyList()
        }
        return excludeCurrentUserFromProviderList(mapped)
    }

    private fun mapDbUserToUser(db: DbUser): User = User(
        id = db.user_id ?: db.id.toString(),
        first_name = db.first_name,
        last_name = db.last_name,
        title = db.title,
        user_name = db.firebaseUid ?: db.user_id ?: "",
        nat_id = db.nat_id,
        dob = db.birth_date,
        gender = db.gender,
        occupation = db.occupation,
        pic = db.prof_pic,
        isIdVerified = null,
        isMobileVerified = null,
        isAvailable = true,
        details = null,
        phoneNumber = db.phone,
        country = db.country,
        county = db.county,
        area_name = db.area_name,
        email = db.email,
        userDbId = db.id,
        isServiceProvider = db.service_provider,
        isProviderApplicationPending = db.provider_application_pending,
    )
}

