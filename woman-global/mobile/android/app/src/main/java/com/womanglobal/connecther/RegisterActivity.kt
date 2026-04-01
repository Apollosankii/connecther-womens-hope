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
import android.view.inputmethod.EditorInfo
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.databinding.ActivityRegisterBinding
import com.womanglobal.connecther.supabase.AuthBridgeClient
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseTokenStore
import com.womanglobal.connecther.utils.PushRegistration
import com.womanglobal.connecther.utils.UserFriendlyMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        val titles = arrayOf("Mr", "Mrs", "Miss", "Sir")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titles)
        binding.titleInput.setAdapter(adapter)
        binding.titleInput.keyListener = null
        binding.titleInput.inputType = EditorInfo.TYPE_NULL
        binding.titleInput.setOnClickListener { binding.titleInput.showDropDown() }

        binding.registerButton.setOnClickListener {
            clearFieldErrors()
            val fistName = binding.firstNameInput.text.toString().trim()
            val lastName = binding.lastNameInput.text.toString().trim()
            val phone = binding.phonNumInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val title = binding.titleInput.text.toString().trim()
            val password = binding.newPasswordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (fistName.isEmpty()) {
                binding.firstNameInputLayout.error = getString(R.string.register_error_first_name)
                return@setOnClickListener
            }

            if (lastName.isEmpty()) {
                binding.lastNameInputLayout.error = getString(R.string.register_error_last_name)
                return@setOnClickListener
            }

            if (phone.isEmpty()) {
                binding.phoneInputLayout.error = getString(R.string.register_error_phone)
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInputLayout.error = getString(R.string.login_error_email)
                return@setOnClickListener
            }

            if (title.isEmpty()) {
                binding.titleInputLayout.error = getString(R.string.register_error_title)
                return@setOnClickListener
            }

            if (password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, R.string.register_error_password_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, R.string.register_error_password_mismatch, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { res ->
                    val user = res.user
                    if (user == null) {
                        setLoading(false)
                        Toast.makeText(this, R.string.auth_register_no_user, Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    user.sendEmailVerification()
                        .addOnCompleteListener {
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

                                            Toast.makeText(
                                                this@RegisterActivity,
                                                getString(R.string.register_success_verify_email),
                                                Toast.LENGTH_LONG,
                                            ).show()
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
                }
                .addOnFailureListener { err ->
                    setLoading(false)
                    Toast.makeText(
                        this,
                        UserFriendlyMessages.firebaseAuth(this, err),
                        Toast.LENGTH_LONG,
                    ).show()
                }
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
}
