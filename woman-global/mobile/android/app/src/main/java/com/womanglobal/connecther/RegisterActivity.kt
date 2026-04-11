package com.womanglobal.connecther

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.womanglobal.connecther.databinding.ActivityRegisterBinding
import com.womanglobal.connecther.supabase.AuthBridgeClient
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseTokenStore
import com.womanglobal.connecther.supabase.TwilioVerifyClient
import com.womanglobal.connecther.utils.ConnectHerPhoneAuth
import com.womanglobal.connecther.utils.PushRegistration
import com.womanglobal.connecther.utils.UserFriendlyMessages
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private fun messageForPhoneVerifyException(e: Exception): String {
        val fe = e as? FirebaseAuthException
        if (fe != null) {
            return UserFriendlyMessages.firebaseAuth(this, fe)
        }
        return getString(R.string.auth_phone_verify_failed) + ": " + (e.message ?: e.javaClass.simpleName)
    }

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var pendingPasswordToSet: String? = null
    /** E.164 after successful Twilio Verify `check` (registration-with-Google flow). */
    private var pendingTwilioVerifiedE164: String? = null

    private val isGoogleProfileCompletion: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_GOOGLE_PROFILE_COMPLETION, false)
    }
    private val isEmailLinkProfileCompletion: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_EMAIL_LINK_PROFILE_COMPLETION, false)
    }
    private val isPhoneProfileCompletion: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_PHONE_PROFILE_COMPLETION, false)
    }

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
                    completeGoogleAfterPhoneVerified(idToken, account.email.orEmpty())
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        if (isGoogleProfileCompletion || isEmailLinkProfileCompletion || isPhoneProfileCompletion) {
            if (auth.currentUser == null) {
                Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            when {
                isGoogleProfileCompletion -> applyGoogleCompletionUi()
                isEmailLinkProfileCompletion -> applyEmailLinkCompletionUi()
                isPhoneProfileCompletion -> applyPhoneCompletionUi()
            }
        } else {
            // No OAuth: fill form + password, verify phone by OTP, then continue with Google.
            binding.emailInputLayout.visibility = View.GONE
            binding.registerButton.text = getString(R.string.register_button_verify_phone)
            binding.continueWithGoogleButton.isEnabled = false
        }

        val titles = arrayOf("Mr", "Mrs", "Miss", "Sir")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titles)
        binding.titleInput.setAdapter(adapter)
        binding.titleInput.keyListener = null
        binding.titleInput.inputType = EditorInfo.TYPE_NULL
        binding.titleInput.setOnClickListener { binding.titleInput.showDropDown() }

        binding.registerButton.setOnClickListener {
            when {
                isGoogleProfileCompletion -> submitGoogleProfileCompletion()
                isEmailLinkProfileCompletion -> submitEmailLinkProfileCompletion()
                isPhoneProfileCompletion -> submitPhoneProfileCompletion()
                else -> startPhoneOtpForRegistration()
            }
        }

        binding.continueWithGoogleButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()
            googleLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
        }

        val registerText = getString(R.string.register_sign_in_prompt)
        val linkLabel = getString(R.string.login_sign_in)
        val linkStart = registerText.indexOf(linkLabel)
        val spannableString = SpannableString(registerText)
        if (linkStart >= 0) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                }
            }
            val linkEnd = linkStart + linkLabel.length
            spannableString.setSpan(clickableSpan, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_color)),
                linkStart,
                linkEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        binding.signInText.text = spannableString
        binding.signInText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun applyGoogleCompletionUi() {
        val user = auth.currentUser ?: return
        binding.registerHeaderTitle.text = getString(R.string.register_google_complete_title)
        binding.registerHeaderSubtitle.text = getString(R.string.register_complete_with_password_subtitle)
        binding.newPasswordInputLayout.visibility = View.VISIBLE
        binding.confirmPasswordInputLayout.visibility = View.VISIBLE
        binding.signInText.visibility = View.GONE
        binding.registerButton.text = getString(R.string.register_google_button)

        binding.emailInput.setText(user.email.orEmpty())
        binding.emailInput.isFocusable = false
        binding.emailInput.isClickable = false
        binding.emailInput.isCursorVisible = false

        val dn = user.displayName?.trim().orEmpty()
        if (dn.isNotEmpty()) {
            val parts = dn.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                binding.firstNameInput.setText(parts.first())
                binding.lastNameInput.setText(parts.drop(1).joinToString(" "))
            } else {
                binding.firstNameInput.setText(parts.first())
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    auth.signOut()
                    startActivity(
                        Intent(this@RegisterActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                    )
                    finish()
                }
            },
        )
    }

    private fun applyEmailLinkCompletionUi() {
        binding.registerHeaderTitle.text = getString(R.string.register_email_link_complete_title)
        binding.registerHeaderSubtitle.text = getString(R.string.register_complete_with_password_subtitle)
        binding.newPasswordInputLayout.visibility = View.VISIBLE
        binding.confirmPasswordInputLayout.visibility = View.VISIBLE
        binding.signInText.visibility = View.GONE
        binding.registerButton.text = getString(R.string.register_google_button)

        val email = sharedPreferences.getString(PREF_PENDING_REG_EMAIL, "")?.trim().orEmpty()
        val first = sharedPreferences.getString(PREF_PENDING_REG_FIRST, "")?.trim().orEmpty()
        val last = sharedPreferences.getString(PREF_PENDING_REG_LAST, "")?.trim().orEmpty()
        val phone = sharedPreferences.getString(PREF_PENDING_REG_PHONE, "")?.trim().orEmpty()
        val title = sharedPreferences.getString(PREF_PENDING_REG_TITLE, "")?.trim().orEmpty()

        if (!sharedPreferences.getBoolean(PREF_PENDING_REGISTRATION, false) || email.isBlank()) {
            Toast.makeText(this, R.string.auth_email_link_complete_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.emailInput.setText(email)
        binding.emailInput.isFocusable = false
        binding.emailInput.isClickable = false
        binding.emailInput.isCursorVisible = false

        binding.firstNameInput.setText(first)
        binding.lastNameInput.setText(last)
        binding.phonNumInput.setText(phone)
        binding.titleInput.setText(title, false)
    }

    private fun applyPhoneCompletionUi() {
        val user = auth.currentUser ?: return
        binding.registerHeaderTitle.text = getString(R.string.register_phone_complete_title)
        binding.registerHeaderSubtitle.text = getString(R.string.register_complete_with_password_subtitle)
        binding.newPasswordInputLayout.visibility = View.VISIBLE
        binding.confirmPasswordInputLayout.visibility = View.VISIBLE
        binding.signInText.visibility = View.GONE
        binding.registerButton.text = getString(R.string.register_google_button)

        val phone = user.phoneNumber?.trim().orEmpty()
        if (phone.isNotBlank()) {
            binding.phonNumInput.setText(phone)
            binding.phonNumInput.isFocusable = false
            binding.phonNumInput.isClickable = false
            binding.phonNumInput.isCursorVisible = false
        }

        user.email?.trim()?.takeIf { it.isNotBlank() }?.let {
            binding.emailInput.setText(it)
        }
    }

    private fun startPhoneOtpForRegistration() {
        clearFieldErrors()
        val fistName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val phone = binding.phonNumInput.text.toString().trim()
        val title = binding.titleInput.text.toString().trim()
        val password = binding.newPasswordInput.text?.toString().orEmpty()
        val confirmPassword = binding.confirmPasswordInput.text?.toString().orEmpty()

        if (fistName.isEmpty()) {
            binding.firstNameInputLayout.error = getString(R.string.register_error_first_name)
            return
        }
        if (lastName.isEmpty()) {
            binding.lastNameInputLayout.error = getString(R.string.register_error_last_name)
            return
        }
        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.register_error_phone)
            return
        }
        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.register_error_title)
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, R.string.register_error_password_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, R.string.register_error_password_mismatch, Toast.LENGTH_SHORT).show()
            return
        }

        val e164 = ConnectHerPhoneAuth.normalizeKenyaE164(phone)
        if (e164 == null) {
            ConnectHerPhoneAuth.toastInvalidPhone(this)
            return
        }

        pendingPasswordToSet = password
        pendingTwilioVerifiedE164 = null

        setLoading(true)
        lifecycleScope.launch {
            try {
                when {
                    auth.currentUser == null -> auth.signInAnonymously().await()
                    auth.currentUser != null && auth.currentUser?.isAnonymous != true -> {
                        setLoading(false)
                        Toast.makeText(
                            this@RegisterActivity,
                            R.string.auth_phone_verify_sign_out_first,
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                }
                val user = auth.currentUser ?: run {
                    setLoading(false)
                    Toast.makeText(this@RegisterActivity, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val token = user.getIdToken(true).await().token?.trim().orEmpty()
                if (token.isEmpty()) {
                    setLoading(false)
                    Toast.makeText(this@RegisterActivity, R.string.auth_phone_verify_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val startRes = withContext(Dispatchers.IO) { TwilioVerifyClient.start(token, e164) }
                if (startRes.isFailure) {
                    setLoading(false)
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.auth_phone_verify_failed) + ": " +
                            (startRes.exceptionOrNull()?.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                setLoading(false)
                Toast.makeText(this@RegisterActivity, R.string.auth_otp_sent, Toast.LENGTH_SHORT).show()
                showTwilioOtpDialog(phoneE164 = e164, cancelSignsOutFirebaseUser = false) {
                    pendingTwilioVerifiedE164 = e164
                    binding.continueWithGoogleButton.isEnabled = true
                    binding.phonNumInput.isEnabled = false
                    applyRegistrationPhoneVerifiedUi()
                    showPhoneVerifiedSuccessDialog()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@RegisterActivity, messageForPhoneVerifyException(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Default registration flow: after Twilio OTP success, show green tick and success prompt before Google. */
    private fun applyRegistrationPhoneVerifiedUi() {
        if (isGoogleProfileCompletion || isEmailLinkProfileCompletion || isPhoneProfileCompletion) return
        binding.registerButton.setIconResource(R.drawable.check_circle_24px)
        binding.registerButton.iconTint =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.profile_verified))
        binding.registerButton.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        val pad = (10 * resources.displayMetrics.density).toInt()
        binding.registerButton.iconPadding = pad
        binding.registerButton.text = getString(R.string.register_button_phone_verified)
        binding.registerButton.isEnabled = false
    }

    private fun showPhoneVerifiedSuccessDialog() {
        if (isGoogleProfileCompletion || isEmailLinkProfileCompletion || isPhoneProfileCompletion) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auth_phone_verified_success_title)
            .setMessage(R.string.auth_phone_verified_success_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun completeGoogleAfterPhoneVerified(idToken: String, googleEmail: String) {
        val phoneRaw = binding.phonNumInput.text.toString().trim()
        val e164 = ConnectHerPhoneAuth.normalizeKenyaE164(phoneRaw)
        if (e164.isNullOrBlank() || e164 != pendingTwilioVerifiedE164) {
            Toast.makeText(this, R.string.auth_phone_verify_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(googleEmail).matches()) {
            Toast.makeText(this, R.string.auth_google_failed, Toast.LENGTH_LONG).show()
            return
        }

        val fistName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val phone = phoneRaw
        val title = binding.titleInput.text.toString().trim()
        val password = pendingPasswordToSet.orEmpty()

        if (fistName.isBlank() || lastName.isBlank() || phone.isBlank() || title.isBlank() || password.length < 6) {
            Toast.makeText(this, R.string.auth_register_no_user, Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)
        val googleCred = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        val wasAnonymous = current?.isAnonymous == true
        val task = if (wasAnonymous && current != null) {
            current.linkWithCredential(googleCred)
        } else {
            auth.signInWithCredential(googleCred)
        }
        task.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
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
            val res = task.result
            val user = res?.user
            val isNewUser = res?.additionalUserInfo?.isNewUser == true
            if (user == null) {
                setLoading(false)
                Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            if (!wasAnonymous && !isNewUser) {
                setLoading(false)
                Toast.makeText(this, R.string.auth_error_email_in_use, Toast.LENGTH_LONG).show()
                auth.signOut()
                return@addOnCompleteListener
            }

            val email = user.email?.trim().orEmpty().ifBlank { googleEmail }
            binding.emailInputLayout.visibility = View.VISIBLE
            binding.emailInput.setText(email)
            binding.emailInput.isFocusable = false
            binding.emailInput.isClickable = false
            binding.emailInput.isCursorVisible = false
            ensurePasswordForFutureLogin(user, email, password) { ok ->
                if (!ok) {
                    auth.signOut()
                    return@ensurePasswordForFutureLogin
                }
                runPostRegisterBridge(user, fistName, lastName, phone, title, email)
            }
        }
    }

    private fun submitEmailLinkProfileCompletion() {
        // Signed in via email link already; now verify phone via OTP and complete profile setup.
        clearFieldErrors()
        val user = auth.currentUser ?: run {
            Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val fistName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val phone = binding.phonNumInput.text.toString().trim()
        val email = user.email?.trim().orEmpty().ifBlank { binding.emailInput.text.toString().trim() }
        val title = binding.titleInput.text.toString().trim()

        if (fistName.isEmpty()) {
            binding.firstNameInputLayout.error = getString(R.string.register_error_first_name)
            return
        }
        if (lastName.isEmpty()) {
            binding.lastNameInputLayout.error = getString(R.string.register_error_last_name)
            return
        }
        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.register_error_phone)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.login_error_email)
            return
        }
        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.register_error_title)
            return
        }

        val password = binding.newPasswordInput.text?.toString().orEmpty()
        val confirmPassword = binding.confirmPasswordInput.text?.toString().orEmpty()
        if (password.length < 6) {
            Toast.makeText(this, R.string.register_error_password_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, R.string.register_error_password_mismatch, Toast.LENGTH_SHORT).show()
            return
        }
        pendingPasswordToSet = password

        val e164 = ConnectHerPhoneAuth.normalizeKenyaE164(phone)
        if (e164 == null) {
            ConnectHerPhoneAuth.toastInvalidPhone(this)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val token = user.getIdToken(true).await().token?.trim().orEmpty()
                if (token.isEmpty()) {
                    setLoading(false)
                    Toast.makeText(this@RegisterActivity, R.string.auth_phone_verify_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val startRes = withContext(Dispatchers.IO) { TwilioVerifyClient.start(token, e164) }
                if (startRes.isFailure) {
                    setLoading(false)
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.auth_phone_verify_failed) + ": " +
                            (startRes.exceptionOrNull()?.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                setLoading(false)
                showTwilioOtpDialog(phoneE164 = e164, cancelSignsOutFirebaseUser = false) {
                    completeProfileAfterTwilioVerified(user, fistName, lastName, phone, title, email)
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@RegisterActivity, messageForPhoneVerifyException(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun submitPhoneProfileCompletion() {
        clearFieldErrors()
        val user = auth.currentUser ?: run {
            Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val fistName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val phone = binding.phonNumInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val title = binding.titleInput.text.toString().trim()

        if (fistName.isEmpty()) {
            binding.firstNameInputLayout.error = getString(R.string.register_error_first_name)
            return
        }
        if (lastName.isEmpty()) {
            binding.lastNameInputLayout.error = getString(R.string.register_error_last_name)
            return
        }
        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.register_error_phone)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.login_error_email)
            return
        }
        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.register_error_title)
            return
        }

        val password = binding.newPasswordInput.text?.toString().orEmpty()
        val confirmPassword = binding.confirmPasswordInput.text?.toString().orEmpty()
        if (password.length < 6) {
            Toast.makeText(this, R.string.register_error_password_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, R.string.register_error_password_mismatch, Toast.LENGTH_SHORT).show()
            return
        }
        pendingPasswordToSet = password

        // Legacy path after Firebase phone sign-in (disabled); set password then complete profile and bridge.
        setLoading(true)
        ensurePasswordForFutureLogin(user, email, password) { ok ->
            if (!ok) return@ensurePasswordForFutureLogin
            runPostRegisterBridge(user, fistName, lastName, phone, title, email)
        }
    }

    // NOTE: Legacy email+password registration removed in favor of email link + phone OTP.

    private fun submitGoogleProfileCompletion() {
        clearFieldErrors()
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val fistName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val phone = binding.phonNumInput.text.toString().trim()
        val email = user.email?.trim().orEmpty().ifBlank { binding.emailInput.text.toString().trim() }
        val title = binding.titleInput.text.toString().trim()

        if (fistName.isEmpty()) {
            binding.firstNameInputLayout.error = getString(R.string.register_error_first_name)
            return
        }

        if (lastName.isEmpty()) {
            binding.lastNameInputLayout.error = getString(R.string.register_error_last_name)
            return
        }

        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.register_error_phone)
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.login_error_email)
            return
        }

        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.register_error_title)
            return
        }

        val password = binding.newPasswordInput.text?.toString().orEmpty()
        val confirmPassword = binding.confirmPasswordInput.text?.toString().orEmpty()
        if (password.length < 6) {
            Toast.makeText(this, R.string.register_error_password_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, R.string.register_error_password_mismatch, Toast.LENGTH_SHORT).show()
            return
        }
        pendingPasswordToSet = password

        val e164 = ConnectHerPhoneAuth.normalizeKenyaE164(phone)
        if (e164 == null) {
            ConnectHerPhoneAuth.toastInvalidPhone(this)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val token = user.getIdToken(true).await().token?.trim().orEmpty()
                if (token.isEmpty()) {
                    setLoading(false)
                    Toast.makeText(this@RegisterActivity, R.string.auth_phone_verify_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val startRes = withContext(Dispatchers.IO) { TwilioVerifyClient.start(token, e164) }
                if (startRes.isFailure) {
                    setLoading(false)
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.auth_phone_verify_failed) + ": " +
                            (startRes.exceptionOrNull()?.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                setLoading(false)
                showTwilioOtpDialog(phoneE164 = e164, cancelSignsOutFirebaseUser = false) {
                    completeProfileAfterTwilioVerified(user, fistName, lastName, phone, title, email)
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@RegisterActivity, messageForPhoneVerifyException(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTwilioOtpDialog(
        phoneE164: String,
        cancelSignsOutFirebaseUser: Boolean,
        onVerified: () -> Unit,
    ) {
        val otpInput = EditText(this).apply {
            hint = getString(R.string.auth_otp_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.register_phone_link_title)
            .setMessage(R.string.register_phone_link_body)
            .setView(otpInput)
            .setPositiveButton(R.string.auth_verify) { _, _ ->
                val code = otpInput.text?.toString()?.trim().orEmpty()
                if (code.length < 4) {
                    Toast.makeText(this, R.string.auth_phone_verify_failed, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val user = auth.currentUser ?: run {
                    Toast.makeText(this, R.string.auth_login_no_user, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                setLoading(true)
                lifecycleScope.launch {
                    try {
                        val token = user.getIdToken(true).await().token?.trim().orEmpty()
                        if (token.isEmpty()) {
                            setLoading(false)
                            Toast.makeText(this@RegisterActivity, R.string.auth_phone_verify_failed, Toast.LENGTH_LONG)
                                .show()
                            return@launch
                        }
                        val res = withContext(Dispatchers.IO) {
                            TwilioVerifyClient.check(token, phoneE164, code)
                        }
                        if (res.isFailure) {
                            setLoading(false)
                            Toast.makeText(
                                this@RegisterActivity,
                                getString(R.string.auth_phone_verify_failed) + ": " +
                                    (res.exceptionOrNull()?.message ?: ""),
                                Toast.LENGTH_LONG,
                            ).show()
                            return@launch
                        }
                        setLoading(false)
                        onVerified()
                    } catch (e: Exception) {
                        setLoading(false)
                        Toast.makeText(this@RegisterActivity, messageForPhoneVerifyException(e), Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
            .apply {
                if (cancelSignsOutFirebaseUser) {
                    setOnCancelListener { auth.signOut() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun completeProfileAfterTwilioVerified(
        user: FirebaseUser,
        fistName: String,
        lastName: String,
        phone: String,
        title: String,
        email: String,
    ) {
        setLoading(true)
        val pass = pendingPasswordToSet
        if (pass.isNullOrBlank()) {
            runPostRegisterBridge(user, fistName, lastName, phone, title, email)
        } else {
            ensurePasswordForFutureLogin(user, email, pass) { ok ->
                if (!ok) {
                    setLoading(false)
                    return@ensurePasswordForFutureLogin
                }
                runPostRegisterBridge(user, fistName, lastName, phone, title, email)
            }
        }
    }

    private fun ensurePasswordForFutureLogin(
        user: FirebaseUser,
        email: String,
        password: String,
        onDone: (Boolean) -> Unit,
    ) {
        val hasEmailProvider = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
        val task =
            if (hasEmailProvider) user.updatePassword(password)
            else user.linkWithCredential(EmailAuthProvider.getCredential(email, password))
        task.addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                setLoading(false)
                Toast.makeText(
                    this,
                    UserFriendlyMessages.firebaseAuth(this, t.exception as? Exception ?: Exception(t.exception)),
                    Toast.LENGTH_LONG,
                ).show()
                onDone(false)
            } else {
                onDone(true)
            }
        }
    }

    private fun runPostRegisterBridge(
        user: FirebaseUser,
        fistName: String,
        lastName: String,
        phone: String,
        title: String,
        email: String,
    ) {
        fun fetchTokenAndSync() {
            user.getIdToken(true)
                .addOnSuccessListener { tokenRes ->
                    val firebaseIdToken = tokenRes.token.orEmpty()
                    CoroutineScope(Dispatchers.IO).launch {
                        val bridgeRes = AuthBridgeClient.exchangeFirebaseIdToken(firebaseIdToken)
                        val bridge = bridgeRes.getOrNull()
                        val bridgeErr = bridgeRes.exceptionOrNull()?.message
                        if (bridge != null) {
                            SupabaseTokenStore.setJwt(bridge.supabaseJwt)
                            sharedPreferences.edit()
                                .putBoolean("isLoggedIn", true)
                                .putBoolean("isFirstLaunch", false)
                                .putString("user_email", user.email ?: email)
                                .putString("firebase_uid", bridge.firebaseUid)
                                .putString("firebase_id_token", firebaseIdToken)
                                .putString("user_id", bridge.userId)
                                .putString("user_full_name", "$fistName $lastName".trim())
                                .putString("user_phone", phone)
                                .apply()
                            if (!SupabaseData.applyRegistrationProfile(
                                    fistName,
                                    lastName,
                                    phone,
                                    title,
                                    user.email ?: email,
                                )
                            ) {
                                android.util.Log.w(
                                    "RegisterActivity",
                                    "applyRegistrationProfile failed; Supabase names may not match form until edited in Settings.",
                                )
                            }
                            SupabaseData.syncLocalProfileFromSupabase(bridge.firebaseUid, sharedPreferences)
                        }
                        runOnUiThread {
                            if (bridge == null) {
                                setLoading(false)
                                val msg = getString(R.string.register_bridge_failed) + "\n" +
                                    UserFriendlyMessages.authBridge(this@RegisterActivity, bridgeErr)
                                Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
                                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                                finish()
                                return@runOnUiThread
                            }
                            if (isEmailLinkProfileCompletion) {
                                sharedPreferences.edit()
                                    .putBoolean(PREF_PENDING_REGISTRATION, false)
                                    .remove(PREF_PENDING_REG_EMAIL)
                                    .remove(PREF_PENDING_REG_FIRST)
                                    .remove(PREF_PENDING_REG_LAST)
                                    .remove(PREF_PENDING_REG_PHONE)
                                    .remove(PREF_PENDING_REG_TITLE)
                                    .apply()
                            }

                            val toastMsg = if (isGoogleProfileCompletion) {
                                getString(R.string.register_success_google)
                            } else if (isPhoneProfileCompletion || isEmailLinkProfileCompletion) {
                                getString(R.string.register_success_google)
                            } else {
                                getString(R.string.register_success_verify_email)
                            }
                            Toast.makeText(this@RegisterActivity, toastMsg, Toast.LENGTH_LONG).show()
                            PushRegistration.register(this@RegisterActivity)
                            startActivity(Intent(this@RegisterActivity, HomeActivity::class.java))
                            finish()
                        }
                    }
                }
                .addOnFailureListener { err ->
                    setLoading(false)
                    Toast.makeText(
                        this@RegisterActivity,
                        UserFriendlyMessages.sessionToken(this@RegisterActivity, err),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }

        if (isGoogleProfileCompletion || isPhoneProfileCompletion || isEmailLinkProfileCompletion) {
            fetchTokenAndSync()
        } else {
            user.sendEmailVerification().addOnCompleteListener { fetchTokenAndSync() }
        }
    }

    private fun clearFieldErrors() {
        binding.titleInputLayout.error = null
        binding.firstNameInputLayout.error = null
        binding.lastNameInputLayout.error = null
        binding.phoneInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.newPasswordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !loading
    }

    companion object {
        const val EXTRA_GOOGLE_PROFILE_COMPLETION = "google_profile_completion"
        const val EXTRA_EMAIL_LINK_PROFILE_COMPLETION = "email_link_profile_completion"
        const val EXTRA_PHONE_PROFILE_COMPLETION = "phone_profile_completion"

        const val PREF_PENDING_REGISTRATION = "pending_registration"
        const val PREF_PENDING_REG_EMAIL = "pending_registration_email"
        const val PREF_PENDING_REG_FIRST = "pending_registration_first"
        const val PREF_PENDING_REG_LAST = "pending_registration_last"
        const val PREF_PENDING_REG_PHONE = "pending_registration_phone"
        const val PREF_PENDING_REG_TITLE = "pending_registration_title"
    }
}
