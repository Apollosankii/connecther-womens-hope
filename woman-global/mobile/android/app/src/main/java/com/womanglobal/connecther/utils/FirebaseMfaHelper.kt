package com.womanglobal.connecther.utils

import android.app.Activity
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.PhoneMultiFactorInfo
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TotpMultiFactorInfo
import com.google.firebase.auth.TotpSecret
import com.womanglobal.connecther.R
import java.util.concurrent.TimeUnit

/**
 * Multi-factor sign-in resolution (SMS / TOTP) and **TOTP enrollment** for Firebase Auth.
 *
 * **Profile phone verification** uses Twilio Verify via the Supabase `phone-verify` Edge Function, not this helper.
 *
 * **SMS MFA enrollment** uses Firebase Phone Auth (separate billing from Twilio). The Settings UI hides the
 * “SMS MFA” enroll button; prefer **TOTP** (Authenticator app). This file remains for MFA challenge flows during
 * sign-in when a user already enrolled SMS factors.
 */
object FirebaseMfaHelper {

    fun startMfaResolution(
        activity: Activity,
        auth: FirebaseAuth,
        resolver: MultiFactorResolver,
        onSuccess: (FirebaseUser) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val phone = resolver.hints.filterIsInstance<PhoneMultiFactorInfo>().firstOrNull()
        val totpInfo = resolver.hints.filterIsInstance<TotpMultiFactorInfo>().firstOrNull()
        when {
            phone != null -> resolveSignInWithPhone(activity, auth, resolver, phone, onSuccess, onFailure)
            totpInfo != null -> resolveSignInWithTotp(activity, resolver, totpInfo, onSuccess, onFailure)
            else -> onFailure(IllegalStateException(activity.getString(R.string.auth_mfa_no_method)))
        }
    }

    private fun resolveSignInWithPhone(
        activity: Activity,
        auth: FirebaseAuth,
        resolver: MultiFactorResolver,
        phoneInfo: PhoneMultiFactorInfo,
        onSuccess: (FirebaseUser) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneInfo.phoneNumber)
            .setTimeout(120L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setMultiFactorSession(resolver.getSession())
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    submitPhoneMfaAssertion(resolver, credential, onSuccess, onFailure)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    onFailure(e)
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    val otpInput = EditText(activity).apply {
                        hint = activity.getString(R.string.auth_otp_hint)
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setPadding(48, 32, 48, 32)
                    }
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.auth_mfa_title)
                        .setMessage(R.string.auth_mfa_enter_sms_code)
                        .setView(otpInput)
                        .setPositiveButton(R.string.auth_verify) { _, _ ->
                            val code = otpInput.text?.toString()?.trim().orEmpty()
                            val cred = PhoneAuthProvider.getCredential(verificationId, code)
                            submitPhoneMfaAssertion(resolver, cred, onSuccess, onFailure)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun submitPhoneMfaAssertion(
        resolver: MultiFactorResolver,
        credential: PhoneAuthCredential,
        onSuccess: (FirebaseUser) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
        resolver.resolveSignIn(assertion)
            .addOnSuccessListener { res ->
                val u = res.user
                if (u != null) onSuccess(u) else onFailure(IllegalStateException("no user"))
            }
            .addOnFailureListener(onFailure)
    }

    private fun resolveSignInWithTotp(
        activity: Activity,
        resolver: MultiFactorResolver,
        totpInfo: TotpMultiFactorInfo,
        onSuccess: (FirebaseUser) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val totpEdit = EditText(activity).apply {
            hint = activity.getString(R.string.auth_mfa_totp_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.auth_mfa_totp_title)
            .setMessage(R.string.auth_mfa_totp_body)
            .setView(totpEdit)
            .setPositiveButton(R.string.auth_verify) { _, _ ->
                val code = totpEdit.text?.toString()?.trim().orEmpty()
                if (code.length < 6) {
                    Toast.makeText(activity, R.string.auth_mfa_invalid_code, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runCatching {
                    val assertion = TotpMultiFactorGenerator.getAssertionForSignIn(totpInfo.uid, code)
                    resolver.resolveSignIn(assertion)
                        .addOnSuccessListener { r ->
                            val u = r.user
                            if (u != null) onSuccess(u) else onFailure(IllegalStateException("no user"))
                        }
                        .addOnFailureListener(onFailure)
                }.onFailure(onFailure)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun enrollSms(
        activity: Activity,
        auth: FirebaseAuth,
        user: FirebaseUser,
        e164Phone: String,
        onEnrolled: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        user.multiFactor.getSession()
            .addOnCompleteListener { sessionTask ->
                if (!sessionTask.isSuccessful) {
                    onFailure(sessionTask.exception ?: IllegalStateException("session"))
                    return@addOnCompleteListener
                }
                val session = sessionTask.result ?: run {
                    onFailure(IllegalStateException("session"))
                    return@addOnCompleteListener
                }
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        enrollPhoneAssertion(user, credential, onEnrolled, onFailure)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        onFailure(e)
                    }

                    override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                        val otpInput = EditText(activity).apply {
                            hint = activity.getString(R.string.auth_otp_hint)
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            setPadding(48, 32, 48, 32)
                        }
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.auth_mfa_enroll_sms_title)
                            .setView(otpInput)
                            .setPositiveButton(R.string.auth_verify) { _, _ ->
                                val code = otpInput.text?.toString()?.trim().orEmpty()
                                val cred = PhoneAuthProvider.getCredential(verificationId, code)
                                enrollPhoneAssertion(user, cred, onEnrolled, onFailure)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(e164Phone)
                    .setTimeout(120L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setMultiFactorSession(session)
                    .setCallbacks(callbacks)
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
            }
    }

    private fun enrollPhoneAssertion(
        user: FirebaseUser,
        credential: PhoneAuthCredential,
        onEnrolled: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
        user.multiFactor.enroll(assertion, "SMS")
            .addOnSuccessListener { onEnrolled() }
            .addOnFailureListener(onFailure)
    }

    fun startTotpEnrollment(
        activity: Activity,
        user: FirebaseUser,
        onEnrolled: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        user.multiFactor.getSession()
            .addOnCompleteListener { sessionTask ->
                if (!sessionTask.isSuccessful) {
                    onFailure(sessionTask.exception ?: IllegalStateException("session"))
                    return@addOnCompleteListener
                }
                val session = sessionTask.result ?: run {
                    onFailure(IllegalStateException("session"))
                    return@addOnCompleteListener
                }
                TotpMultiFactorGenerator.generateSecret(session)
                    .addOnSuccessListener { secret ->
                        showTotpEnrollmentDialog(activity, user, secret, onEnrolled, onFailure)
                    }
                    .addOnFailureListener(onFailure)
            }
    }

    private fun showTotpEnrollmentDialog(
        activity: Activity,
        user: FirebaseUser,
        secret: TotpSecret,
        onEnrolled: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val issuer = activity.getString(R.string.app_name)
        val account = user.email ?: user.uid
        val qrUrl = runCatching { secret.generateQrCodeUrl(account, issuer) }.getOrElse {
            onFailure(it)
            return
        }
        val codeInput = EditText(activity).apply {
            hint = activity.getString(R.string.auth_mfa_totp_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.auth_mfa_enroll_totp_title)
            .setMessage(activity.getString(R.string.auth_mfa_enroll_totp_message, qrUrl))
            .setView(codeInput)
            .setPositiveButton(R.string.auth_verify) { _, _ ->
                val code = codeInput.text?.toString()?.trim().orEmpty()
                if (code.length < 6) {
                    Toast.makeText(activity, R.string.auth_mfa_invalid_code, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runCatching {
                    val assertion = TotpMultiFactorGenerator.getAssertionForEnrollment(secret, code)
                    user.multiFactor.enroll(assertion, activity.getString(R.string.auth_mfa_totp_display_name))
                        .addOnSuccessListener { onEnrolled() }
                        .addOnFailureListener(onFailure)
                }.onFailure(onFailure)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
