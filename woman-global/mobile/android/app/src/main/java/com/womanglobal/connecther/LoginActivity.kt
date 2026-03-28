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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.womanglobal.connecther.databinding.ActivityLoginBinding
import com.womanglobal.connecther.supabase.AuthBridgeClient
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseTokenStore
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
                .onFailure { Toast.makeText(this, it.message ?: "Google sign-in failed", Toast.LENGTH_LONG).show() }
                .onSuccess { account ->
                    val idToken = account.idToken
                    if (idToken.isNullOrBlank()) {
                        Toast.makeText(this, "Google sign-in not configured (missing ID token).", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    signInWithGoogle(idToken)
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()

            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInput.error = "Enter a valid email"
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.passwordInput.error = "Password is required"
                return@setOnClickListener
            }

            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { res ->
                    val user = res.user
                    if (user == null) {
                        setLoading(false)
                        Toast.makeText(this, "Login failed (no user)", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

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
                                        .putString("user_email", user.email ?: email)
                                        .putString("firebase_uid", bridge.firebaseUid)
                                        .putString("firebase_id_token", idToken)
                                        .putString("user_id", bridge.userId)
                                        .apply()
                                    SupabaseData.syncLocalProfileFromSupabase(bridge.firebaseUid, sharedPreferences)
                                }
                                runOnUiThread {
                                    if (bridge == null) {
                                        setLoading(false)
                                        Toast.makeText(this@LoginActivity, bridgeErr ?: "Session setup failed. Please try again.", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(this, err.message ?: "Failed to get session token", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { err ->
                    setLoading(false)
                    Toast.makeText(this, err.message ?: "Login failed", Toast.LENGTH_LONG).show()
                }
        }

        binding.googleSignInButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                // NOTE: requires proper google-services.json OAuth client setup
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()
            googleLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
        }

        val registerText = "Don't have an account? Register"
        val spannableString = SpannableString(registerText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            }
        }
        spannableString.setSpan(clickableSpan, 23, 31, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val colorSpan = ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_color))
        spannableString.setSpan(colorSpan, 23, 31, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.registerText.text = spannableString
        binding.registerText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun signInWithGoogle(idToken: String) {
        setLoading(true)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { res ->
                val user = res.user
                if (user == null) {
                    setLoading(false)
                    Toast.makeText(this, "Google sign-in failed (no user)", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                user.getIdToken(true)
                    .addOnSuccessListener { tokenRes ->
                        val freshToken = tokenRes.token.orEmpty()
                        CoroutineScope(Dispatchers.IO).launch {
                            val bridgeRes = AuthBridgeClient.exchangeFirebaseIdToken(freshToken)
                            val bridge = bridgeRes.getOrNull()
                            val bridgeErr = bridgeRes.exceptionOrNull()?.message
                            if (bridge != null) {
                                SupabaseTokenStore.setJwt(bridge.supabaseJwt)
                                sharedPreferences.edit()
                                    .putBoolean("isLoggedIn", true)
                                    .putBoolean("isFirstLaunch", false)
                                    .putString("user_email", user.email ?: "")
                                    .putString("firebase_uid", bridge.firebaseUid)
                                    .putString("firebase_id_token", freshToken)
                                    .putString("user_id", bridge.userId)
                                    .apply()
                                SupabaseData.syncLocalProfileFromSupabase(bridge.firebaseUid, sharedPreferences)
                            }
                            runOnUiThread {
                                if (bridge == null) {
                                    setLoading(false)
                                    Toast.makeText(this@LoginActivity, bridgeErr ?: "Session setup failed. Please try again.", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this, err.message ?: "Failed to get session token", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { err ->
                setLoading(false)
                Toast.makeText(this, err.message ?: "Google sign-in failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.visibility = if (loading) View.GONE else View.VISIBLE
        binding.googleSignInButton.isEnabled = !loading
    }

    private fun registerDeviceForPush() {
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ).orEmpty()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseData.upsertFcmToken(token.orEmpty(), deviceId)
            }
        }
    }
}

