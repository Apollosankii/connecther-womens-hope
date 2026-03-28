package com.womanglobal.connecther.repository

import android.util.Log
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

/**
 * Database-facing repository for Supabase tables used by Firebase auth.
 *
 * [syncAuthenticatedFirebaseUser] upserts into [TABLE_FIREBASE_USERS] (see SQL migration).
 * Your main profile table (`users`) still stores the Firebase UID in `clerk_user_id` for RLS compatibility.
 */
class SupabaseRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {

    /**
     * After any successful Firebase sign-in, ensure a row exists in `firebase_auth_users`:
     * if no row for [uid], insert ([uid], [email]); if it exists, do nothing.
     */
    suspend fun syncAuthenticatedFirebaseUser(uid: String, email: String?) {
        if (!SupabaseData.isConfigured()) return
        if (!SupabaseClientProvider.ensureFirebaseSession()) {
            Log.w(TAG, "syncAuthenticatedFirebaseUser: no Firebase session")
            return
        }
        val rows = runCatching {
            client.from(TABLE_FIREBASE_USERS).select {
                filter { eq("id", uid) }
            }.decodeList<FirebaseAuthUserRow>()
        }.getOrElse { e ->
            Log.e(TAG, "select firebase_auth_users failed", e)
            return
        }
        if (rows.isNotEmpty()) return
        runCatching {
            client.from(TABLE_FIREBASE_USERS).insert(
                FirebaseAuthUserRow(id = uid, email = email.orEmpty()),
            )
        }.onFailure { e ->
            if (SupabaseData.isUserAlreadyExistsError(e)) return@onFailure
            Log.e(TAG, "insert firebase_auth_users failed", e)
        }
    }

    companion object {
        private const val TAG = "SupabaseRepository"
        /** Public table name; matches migration file. */
        const val TABLE_FIREBASE_USERS = "firebase_auth_users"
    }
}

@Serializable
data class FirebaseAuthUserRow(
    val id: String,
    val email: String,
)
