package com.womanglobal.connecther.auth

import com.google.firebase.auth.FirebaseAuthException

/** Maps [FirebaseAuthException.errorCode] to short, user-facing messages. */
object FirebaseAuthErrorMapper {

    fun message(throwable: Throwable): String {
        val e = throwable as? FirebaseAuthException
            ?: return throwable.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."

        return if (e.message.isNullOrBlank()) {
            "Authentication failed."
        } else {
            when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "That email address doesn’t look valid."
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                -> "Incorrect email or password."
                "ERROR_USER_NOT_FOUND" -> "No account found for this email."
                "ERROR_USER_DISABLED" -> "This account has been disabled."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists with this email."
                "ERROR_WEAK_PASSWORD" -> "Password is too weak. Use at least 6 characters."
                "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is not enabled in Firebase."
                "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your connection and try again."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
                else -> e.message ?: "Authentication failed."
            }
        }
    }
}
