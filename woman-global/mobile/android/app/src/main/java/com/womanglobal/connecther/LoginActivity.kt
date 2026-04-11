package com.womanglobal.connecther

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.view.View
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.womanglobal.connecther.databinding.ActivityLoginBinding
import com.womanglobal.connecther.supabase.AuthBridgeClient
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseTokenStore
import com.womanglobal.connecther.utils.FirebaseEmailLinkAuth
import com.womanglobal.connecther.utils.FirebaseMfaHelper
import com.womanglobal.connecther.utils.PushRegistration
import com.womanglobal.connecther.utils.UserFriendlyMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching { task.getResult(ApiException::class.java) }
                .onFailure { e ->
                    Toast.makeText(
                        this,
                        UserFriendlyMessages.firebaseGoogle(this, e as? Exception ?: Exception(e)),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onSuccess { account ->
                    val idToken = account.idToken
                    if (idToken.isNullOrBlank()) {
                        Toast.makeText(this, R.string.auth_google_failed, Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    signInWithGoogle(idToken, account.email ?: "")
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        processEmailLinkIntent(intent)

        binding.emailLinkSignInButton.setOnClickListener {
            binding.emailInputLayout.error = null
            val email = binding.emailInput.text.toString().trim()
            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInputLayout.error = getString(R.string.login_error_email)
                return@setOnClickListener
            }
            setLoading(true)
            sharedPreferences.edit().putString(FirebaseEmailLinkAuth.PREF_PENDING_EMAIL, email).apply()
            FirebaseEmailLinkAuth.sendSignInLink(
                auth,
                email,
                this,
                onSuccess = {
                    setLoading(false)
                    Toast.makeText(this, R.string.auth_email_link_sent, Toast.LENGTH_LONG).show()
                },
                onFailure = { msg ->
                    setLoading(false)
                    sharedPreferences.edit().remove(FirebaseEmailLinkAuth.PREF_PENDING_EMAIL).apply()
                    Toast.makeText(
                        this,
                        getString(R.string.auth_email_link_failed) + (if (msg.isNotBlank()) ": $msg" else ""),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }

        binding.loginButton.setOnClickListener {
            binding.emailInputLayout.error = null
            binding.passwordInputLayout.error = null

            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()

            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInputLayout.error = getString(R.string.login_error_email)
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.passwordInputLayout.error = getString(R.string.login_error_password)
                return@setOnClickListener
            }

            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task -> handleAuthTask(task, email) }
        }

        binding.forgotPasswordText.setOnClickListener { showForgotPasswordDialog() }

        binding.googleSignInButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                // NOTE: requires proper google-services.json OAuth client setup
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()
            googleLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
        }

        val registerPrompt = getString(R.string.login_register_prompt)
        val registerLink = getString(R.string.login_register_link)
        val spannableString = SpannableString(registerPrompt)
        val linkStart = registerPrompt.indexOf(registerLink)
        if (linkStart >= 0) {
            val linkEnd = linkStart + registerLink.length
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                }
            }
            spannableString.setSpan(clickableSpan, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val colorSpan = ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_color))
            spannableString.setSpan(colorSpan, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.registerText.text = spannableString
        binding.registerText.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processEmailLinkIntent(intent)
    }

    private fun processEmailLinkIntent(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (!auth.isSignInWithEmailLink(link)) return
        val email = sharedPreferences.getString(FirebaseEmailLinkAuth.PREF_PENDING_EMAIL, null)?.trim().orEmpty()
        if (email.isBlank()) {
            Toast.makeText(this, R.string.auth_email_link_need_email, Toast.LENGTH_LONG).show()
            return
        }
        setLoading(true)
        auth.signInWithEmailLink(email, link)
            .addOnCompleteListener { task -> handleAuthTask(task, email) }
    }

    private fun onFirebaseAuthSuccess(
        user: FirebaseUser,
        emailForPrefs: String,
        isNewGoogleAccount: Boolean = false,
        hasPendingEmailLinkRegistration: Boolean = false,
    ) {
        if (UserFriendlyMessages.requiresPasswordVerification(user)) {
            setLoading(false)
            showEmailVerificationDialog(user)
            return
        }
        if (hasPendingEmailLinkRegistration) {
            setLoading(false)
            startActivity(
                Intent(this, RegisterActivity::class.java).apply {
                    putExtra(RegisterActivity.EXTRA_EMAIL_LINK_PROFILE_COMPLETION, true)
                },
            )
            finish()
            return
        }
        if (isNewGoogleAccount) {
            setLoading(false)
            startActivity(
                Intent(this, RegisterActivity::class.java).apply {
                    putExtra(RegisterActivity.EXTRA_GOOGLE_PROFILE_COMPLETION, true)
                },
            )
            finish()
            return
        }
        exchangeBridgeAndGoHome(user, user.email ?: emailForPrefs)
    }

    private fun handleAuthTask(task: Task<AuthResult>, emailForPrefs: String) {
        if (task.isSuccessful) {
            val user = task.result?.user
            if (user == null) {
                setLoading(false)
                Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
            } else {
                sharedPreferences.edit().remove(FirebaseEmailLinkAuth.PREF_PENDING_EMAIL).apply()
                setIntent(Intent(this, LoginActivity::class.java))
                val isNewUser = task.result?.additionalUserInfo?.isNewUser == true
                val isNewGoogleAccount =
                    isNewUser && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
                val hasPendingEmailLinkRegistration =
                    sharedPreferences.getBoolean(RegisterActivity.PREF_PENDING_REGISTRATION, false)
                onFirebaseAuthSuccess(
                    user = user,
                    emailForPrefs = user.email ?: emailForPrefs,
                    isNewGoogleAccount = isNewGoogleAccount,
                    hasPendingEmailLinkRegistration = hasPendingEmailLinkRegistration,
                )
            }
        } else {
            when (val e = task.exception) {
                is FirebaseAuthMultiFactorException -> {
                    setLoading(false)
                    FirebaseMfaHelper.startMfaResolution(
                        this,
                        auth,
                        e.resolver,
                        onSuccess = { u ->
                            sharedPreferences.edit().remove(FirebaseEmailLinkAuth.PREF_PENDING_EMAIL).apply()
                            onFirebaseAuthSuccess(
                                u,
                                u.email ?: emailForPrefs,
                                isNewGoogleAccount = false,
                                hasPendingEmailLinkRegistration = false,
                            )
                        },
                        onFailure = { err ->
                            Toast.makeText(
                                this,
                                UserFriendlyMessages.firebaseAuth(this, err as? Exception ?: Exception(err)),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
                else -> {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        UserFriendlyMessages.firebaseAuth(this, e as? Exception ?: Exception(e)),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun exchangeBridgeAndGoHome(user: com.google.firebase.auth.FirebaseUser, emailForPrefs: String) {
        user.getIdToken(true)
            .addOnSuccessListener { tokenRes ->
                val idToken = tokenRes.token.orEmpty()
                CoroutineScope(Dispatchers.IO).launch {
                    val bridgeRes = AuthBridgeClient.exchangeFirebaseIdToken(idToken)
                    val bridge = bridgeRes.getOrNull()
                    val bridgeErr = bridgeRes.exceptionOrNull()?.message
                    if (bridge != null) {
                        SupabaseTokenStore.setJwt(bridge.supabaseJwt)
                        sharedPreferences.edit()
                            .putBoolean("isLoggedIn", true)
                            .putBoolean("isFirstLaunch", false)
                            .putString("user_email", user.email ?: emailForPrefs)
                            .putString("firebase_uid", bridge.firebaseUid)
                            .putString("firebase_id_token", idToken)
                            .putString("user_id", bridge.userId)
                            .apply()
                        SupabaseData.syncLocalProfileFromSupabase(bridge.firebaseUid, sharedPreferences)
                    }
                    runOnUiThread {
                        if (bridge == null) {
                            setLoading(false)
                            Toast.makeText(
                                this@LoginActivity,
                                UserFriendlyMessages.authBridge(this@LoginActivity, bridgeErr),
                                Toast.LENGTH_LONG,
                            ).show()
                            return@runOnUiThread
                        }
                        registerDeviceForPush()
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }
                }
            }
            .addOnFailureListener { err ->
                setLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    UserFriendlyMessages.sessionToken(this@LoginActivity, err),
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    private fun signInWithGoogle(idToken: String, accountEmailFallback: String) {
        setLoading(true)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnCompleteListener { task ->
                if (!task.isSuccessful && task.exception !is FirebaseAuthMultiFactorException) {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        UserFriendlyMessages.firebaseGoogle(
                            this,
                            task.exception as? Exception ?: Exception(task.exception),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@addOnCompleteListener
                }
                handleAuthTask(task, task.result?.user?.email ?: accountEmailFallback)
            }
    }

    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailLayout = dialogView.findViewById<TextInputLayout>(R.id.forgotEmailLayout)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.forgotEmailInput)
        emailInput.setText(binding.emailInput.text?.toString().orEmpty())

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.forgot_password_title)
            .setMessage(R.string.forgot_password_message)
            .setView(dialogView)
            .setPositiveButton(R.string.forgot_password_send_link, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val sendBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            sendBtn.setOnClickListener {
                emailLayout.error = null
                val resetEmail = emailInput.text?.toString()?.trim().orEmpty()
                if (resetEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(resetEmail).matches()) {
                    emailLayout.error = getString(R.string.login_error_email)
                    return@setOnClickListener
                }
                sendBtn.isEnabled = false
                auth.sendPasswordResetEmail(resetEmail)
                    .addOnCompleteListener { task ->
                        sendBtn.isEnabled = true
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            Toast.makeText(this, R.string.forgot_password_sent, Toast.LENGTH_LONG).show()
                        } else {
                            val ex = task.exception as? Exception ?: Exception("reset failed")
                            Toast.makeText(
                                this,
                                UserFriendlyMessages.firebasePasswordReset(this, ex),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
            }
        }
        dialog.show()
    }

    private fun showEmailVerificationDialog(user: com.google.firebase.auth.FirebaseUser) {
        AlertDialog.Builder(this)
            .setTitle(R.string.auth_verify_email_title)
            .setMessage(R.string.auth_verify_email_body)
            .setPositiveButton(R.string.auth_resend_verification) { _, _ ->
                user.sendEmailVerification().addOnCompleteListener { t ->
                    val msg = if (t.isSuccessful) {
                        getString(R.string.auth_verification_resent)
                    } else {
                        getString(R.string.auth_verification_send_failed)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.auth_sign_out) { _, _ -> auth.signOut() }
            .setCancelable(true)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.visibility = if (loading) View.GONE else View.VISIBLE
        binding.googleSignInButton.isEnabled = !loading
        binding.emailLinkSignInButton.isEnabled = !loading
        binding.forgotPasswordText.isEnabled = !loading
    }

    private fun registerDeviceForPush() {
        PushRegistration.register(this)
    }
}

