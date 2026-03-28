package com.womanglobal.connecther

import ApiServiceFactory
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.womanglobal.connecther.databinding.ActivityRegisterBinding
import com.womanglobal.connecther.services.GetProfileResponse
import com.womanglobal.connecther.services.LoginRequest
import com.womanglobal.connecther.services.LoginResponse
import com.womanglobal.connecther.services.RegisterRequest
import com.womanglobal.connecther.services.RegisterResponse
import com.womanglobal.connecther.utils.CurrentUser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val apiService = ApiServiceFactory.createApiService() // Moved API service to class level


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

            val userId = UUID.randomUUID().toString() // Generating a unique user ID

            val registerRequest = RegisterRequest(title, fistName,lastName,phone,email,userId,password)

            apiService.registerUser(registerRequest).enqueue(object : Callback<RegisterResponse> {
                override fun onResponse(call: Call<RegisterResponse>, response: Response<RegisterResponse>) {
                    Log.d("REGISTRATION", "---------------------> REGISTRATION Request:  $registerRequest")
                    Log.d("REGISTRATION", "---------------------> REGISTRATION Response:  $response")

                    if (response.isSuccessful && response.body() != null) {
                        val registerResponse = response.body()!!
                        Log.d("Register", "Response: ${registerResponse.info}, Status: ${registerResponse.response}")

                        Toast.makeText(this@RegisterActivity, "Registered successfully", Toast.LENGTH_SHORT).show()

                        loginUser(phone = phone, password = password, email)

                    } else {
                        Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                    Toast.makeText(this@RegisterActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
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

    private fun loginUser(phone: String, password: String, email: String) {
        val loginRequest = LoginRequest(phone, password)

        binding.registerButton.visibility = View.GONE

        apiService.loginUser(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                binding.registerButton.visibility = View.VISIBLE

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val authToken = loginResponse.token

                    // Save auth token in shared preferences
                    with(sharedPreferences.edit()) {
                        putString("auth_token", authToken)
                        putBoolean("isLoggedIn", true)
                        apply()
                    }

                    fetchUserProfile(authToken, phone, email)
                } else {
                    Toast.makeText(this@RegisterActivity, "Login failed after registration", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                binding.registerButton.visibility = View.VISIBLE
                Toast.makeText(this@RegisterActivity, "Login error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchUserProfile(authToken: String, phone: String, email : String) {
        val headers = mapOf(
            "Authorization" to "Bearer $authToken",
            "Accept" to "application/json"
        )

        // Log Headers Before Making the Request
        Log.e("Patrice", "Fetching User Profile with Headers: $headers")

        apiService.getUserProfile().enqueue(object : Callback<GetProfileResponse> {
            override fun onResponse(call: Call<GetProfileResponse>, response: Response<GetProfileResponse>) {
                Log.e("Patrice", "Profile Response Code: ${response.code()}")
                Log.e("Patrice", "Profile Response Body: ${response.body()?.toString()}")
                Log.e("Patrice", "Profile Response Error Body: ${response.errorBody()?.string()}")

                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!.profile

                    Log.e("Patrice", "User Data: $user")

                    // Save user details in SharedPreferences
                    with(sharedPreferences.edit()) {
                        putString("user_full_name", "${user.first_name} ${user.last_name}")
                        putString("user_phone", phone)
                        putString("user_email", email)
                        putString("user_pic", user.pic)
                        putString("user_id", user.user_name)
                        apply()
                    }

                    // Save in CurrentUser
                    CurrentUser.setUser(user)

                    // Navigate to HomeActivity
                    val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.e("Patrice", "Failed to fetch profile. Response Code: ${response.code()}")
                    Log.e("Patrice", "Error Response Body: ${response.errorBody()?.string()}")
                    Toast.makeText(this@RegisterActivity, "Failed to fetch user profile", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GetProfileResponse>, t: Throwable) {
                Log.e("Patrice", "Error fetching profile: ${t.message}")
                Toast.makeText(this@RegisterActivity, "Error fetching profile: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
