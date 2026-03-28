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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.databinding.ActivityRegisterBinding
import com.womanglobal.connecther.supabase.AuthBridgeClient
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseTokenStore
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

        val titles = arrayOf("Mr", "Mrs","Miss", "Sir")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titles)
        binding.titleInput.setAdapter(adapter)
        binding.titleInput.inputType = 0
        binding.titleInput.setOnClickListener {
            binding.titleInput.showDropDown()
        }


        binding.registerButton.setOnClickListener {
            val fistName = binding.firstNameInput.text.toString().trim()
            val lastName = binding.lastNameInput.text.toString().trim()
            val phone = binding.phonNumInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val title = binding.titleInput.text.toString().trim()
            val password = binding.newPasswordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (fistName.isEmpty()) {
                binding.firstNameInput.error = "First names are required"
                return@setOnClickListener
            }

            if (lastName.isEmpty()) {
                binding.lastNameInput.error = "Last name is required"
                return@setOnClickListener
            }

            if (phone.isEmpty()) {
                binding.phonNumInput.error = "Phone number is required"
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInput.error = "Enter a valid email"
                return@setOnClickListener
            }

            if (title.isEmpty()) {
                binding.titleInput.error = "Title is required"
                return@setOnClickListener
            }

            if (password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Password fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { res ->
                    val user = res.user
                    if (user == null) {
                        setLoading(false)
                        Toast.makeText(this, "Registration failed (no user)", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

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
                                    SupabaseData.syncLocalProfileFromSupabase(bridge.firebaseUid, sharedPreferences)
                                }
                                runOnUiThread {
                                    if (bridge == null) {
                                        setLoading(false)
                                        Toast.makeText(
                                            this@RegisterActivity,
                                            bridgeErr ?: "Account created, but session setup failed. Please sign in.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                                        finish()
                                        return@runOnUiThread
                                    }

                                    Toast.makeText(this@RegisterActivity, "Registered successfully", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@RegisterActivity, HomeActivity::class.java))
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
                    Toast.makeText(this, err.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                }
        }


        // Register clickable span
        val registerText = "Already have an account? Sign in"
        val spannableString = SpannableString(registerText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                startActivity(intent)
            }
        }
        spannableString.setSpan(clickableSpan, 24, 32, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val colorSpan = ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_color))
        spannableString.setSpan(colorSpan, 24, 32, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.signInText.text = spannableString
        binding.signInText.movementMethod = LinkMovementMethod.getInstance()

    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !loading
    }
}
