package com.womanglobal.connecther.utils

import android.content.Context
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.womanglobal.connecther.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object UserFriendlyMessages {

    fun firebaseAuth(context: Context, e: Exception): String {
        val fe = e as? FirebaseAuthException
        val code = fe?.errorCode?.uppercase().orEmpty()
        return when (code) {
            "ERROR_INVALID_EMAIL" -> context.getString(R.string.auth_error_invalid_email)
            "ERROR_WRONG_PASSWORD" -> context.getString(R.string.auth_error_wrong_password)
            "ERROR_USER_NOT_FOUND" -> context.getString(R.string.auth_error_user_not_found)
            "ERROR_USER_DISABLED" -> context.getString(R.string.auth_error_user_disabled)
            "ERROR_EMAIL_ALREADY_IN_USE" -> context.getString(R.string.auth_error_email_in_use)
            "ERROR_WEAK_PASSWORD" -> context.getString(R.string.auth_error_weak_password)
            "ERROR_TOO_MANY_REQUESTS" -> context.getString(R.string.auth_error_too_many_requests)
            "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.auth_error_network)
            "ERROR_INVALID_CREDENTIAL" -> context.getString(R.string.auth_error_invalid_credential)
            "ERROR_OPERATION_NOT_ALLOWED" -> context.getString(R.string.auth_error_not_allowed)
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                context.getString(R.string.auth_error_account_exists_different)
            else -> if (code.isNotBlank()) {
                context.getString(R.string.auth_error_generic)
            } else {
                networkOrGeneric(context, e)
            }
        }
    }

    fun firebaseGoogle(context: Context, e: Exception): String {
        val msg = e.message?.lowercase().orEmpty()
        return when {
            msg.contains("network") || msg.contains("unable to resolve") ->
                context.getString(R.string.auth_error_network)
            msg.contains("cancel") || msg.contains("12501") ->
                context.getString(R.string.auth_google_cancelled)
            else -> context.getString(R.string.auth_google_failed)
        }
    }

    /** [FirebaseAuth.sendPasswordResetEmail] failures (success is handled separately in UI). */
    fun firebasePasswordReset(context: Context, e: Exception): String {
        val fe = e as? FirebaseAuthException
        return when (fe?.errorCode?.uppercase()) {
            "ERROR_INVALID_EMAIL" -> context.getString(R.string.auth_error_invalid_email)
            "ERROR_TOO_MANY_REQUESTS" -> context.getString(R.string.auth_error_too_many_requests)
            "ERROR_NETWORK_REQUEST_FAILED" -> context.getString(R.string.auth_error_network)
            "ERROR_USER_NOT_FOUND" -> context.getString(R.string.forgot_password_sent)
            else -> if ((fe?.errorCode ?: "").isNotBlank()) {
                context.getString(R.string.forgot_password_failed)
            } else {
                networkOrGeneric(context, e).let { net ->
                    if (net == context.getString(R.string.auth_error_network)) net
                    else context.getString(R.string.forgot_password_failed)
                }
            }
        }
    }

    fun authBridge(context: Context, raw: String?): String {
        val m = raw?.lowercase().orEmpty()
        return when {
            m.isBlank() -> context.getString(R.string.error_session_setup)
            m.contains("unable to resolve host") || m.contains("unknownhost") ->
                context.getString(R.string.error_no_internet_short)
            m.contains("failed to connect") || m.contains("econnrefused") ->
                context.getString(R.string.error_no_internet_short)
            m.contains("timeout") || m.contains("timed out") ->
                context.getString(R.string.network_request_timeout)
            m.contains("401") || m.contains("unauthorized") ->
                context.getString(R.string.error_session_denied)
            m.contains("403") || m.contains("forbidden") ->
                context.getString(R.string.error_session_denied)
            m.contains("500") || m.contains("502") || m.contains("503") ->
                context.getString(R.string.error_server_busy)
            else -> context.getString(R.string.error_session_setup)
        }
    }

    fun paystackInit(context: Context, e: Throwable): String {
        val m = e.message?.lowercase().orEmpty()
        return when {
            isOfflineLike(e) -> context.getString(R.string.paystack_error_offline)
            m.contains("missing authenticated jwt") || m.contains("401") || m.contains("403") ->
                context.getString(R.string.paystack_error_session)
            m.contains("paystack-express init failed") && m.contains("404") ->
                context.getString(R.string.paystack_error_server)
            m.contains("paystack-express init failed") && m.contains("500") ->
                context.getString(R.string.error_server_busy)
            m.contains("paystack-checkout") || m.contains("access_code") ->
                context.getString(R.string.paystack_error_checkout)
            else -> context.getString(R.string.paystack_error_generic)
        }
    }

    fun paystackSheetUserMessage(context: Context, technical: String?): String {
        val m = technical?.lowercase().orEmpty()
        return when {
            m.isBlank() -> context.getString(R.string.paystack_error_sheet_generic)
            isOfflineMessage(m) -> context.getString(R.string.paystack_error_offline)
            m.contains("cancel") -> context.getString(R.string.paystack_cancelled)
            m.contains("network") || m.contains("ssl") || m.contains("connection") ->
                context.getString(R.string.paystack_error_offline)
            else -> context.getString(R.string.paystack_error_sheet_generic)
        }
    }

    fun sessionToken(context: Context, e: Exception): String =
        if (isOfflineLike(e)) {
            context.getString(R.string.error_no_internet_short)
        } else {
            context.getString(R.string.error_session_token)
        }

    fun requiresPasswordVerification(user: FirebaseUser): Boolean =
        !user.isEmailVerified &&
            user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

    private fun isOfflineLike(e: Throwable): Boolean {
        if (e is UnknownHostException || e is SocketTimeoutException || e is SSLException || e is ConnectException) {
            return true
        }
        val m = e.message?.lowercase().orEmpty()
        return m.contains("unable to resolve") ||
            m.contains("failed to connect") ||
            m.contains("network is unreachable") ||
            m.contains("econnrefused")
    }

    private fun isOfflineMessage(m: String): Boolean =
        m.contains("unable to resolve") ||
            m.contains("network") ||
            m.contains("unreachable") ||
            m.contains("timeout") ||
            m.contains("connection") ||
            m.contains("failed to connect")

    private fun networkOrGeneric(context: Context, e: Exception): String =
        if (isOfflineLike(e)) {
            context.getString(R.string.auth_error_network)
        } else {
            context.getString(R.string.auth_error_generic)
        }
}
