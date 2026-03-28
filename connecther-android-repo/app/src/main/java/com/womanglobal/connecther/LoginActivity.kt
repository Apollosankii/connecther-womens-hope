package com.womanglobal.connecther

import ApiServiceFactory
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.womanglobal.connecther.databinding.ActivityLoginBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.DeviceTokenRequest
import com.womanglobal.connecther.services.GetProfileResponse
import com.womanglobal.connecther.services.LoginRequest
import com.womanglobal.connecther.services.LoginResponse
import com.womanglobal.connecther.services.NotificationService.Companion.TAG
import com.womanglobal.connecther.utils.CurrentUser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        binding.loginButton.setOnClickListener {
            val phone = binding.phoneInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (phone.isEmpty() || !Patterns.PHONE.matcher(phone).matches()) {
                binding.phoneInput.error = "Please enter a valid phone number"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.passwordInput.error = "Password is required"
                return@setOnClickListener
            }

            loginUser(phone, password)
        }

        // Register clickable span
        val registerText = "Don't have an account? Register"
        val spannableString = SpannableString(registerText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                startActivity(intent)
            }
        }
        spannableString.setSpan(clickableSpan, 23, 31, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val colorSpan = ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_color))
        spannableString.setSpan(colorSpan, 23, 31, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.registerText.text = spannableString
        binding.registerText.movementMethod = LinkMovementMethod.getInstance()
    }


    private fun loginUser(phone: String, password: String) {
        apiService = ApiServiceFactory.createApiService()
        val loginRequest = LoginRequest(phone, password)

        // Show progress bar, hide button
        binding.progressBar.visibility = View.VISIBLE
        binding.loginButton.visibility = View.GONE

        apiService.loginUser(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val authToken = loginResponse.token
                    val isProvider = loginResponse.provider // Get provider status

                    // Save token & provider status
                    with(sharedPreferences.edit()) {
                        putString("auth_token", authToken)
                        putBoolean("isLoggedIn", true)
                        putBoolean("isProvider", isProvider) // Save provider status
                        apply()
                    }

                    // Fetch user profile after login
                    fetchUserProfile(authToken, phone)

                    updateDeviceToken(authToken)
                }
            }
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                binding.loginButton.visibility = View.VISIBLE
                Toast.makeText(this@LoginActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun fetchUserProfile(authToken: String, phone : String) {
        val headers = mapOf(
            "Authorization" to "Bearer $authToken",
            "Accept" to "application/json"
        )

        // Log Headers Before Making the Request
        Log.e("Patrice", "Fetching User Profile with Headers: $headers")

        apiService.getUserProfile().enqueue(object : Callback<GetProfileResponse> {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<GetProfileResponse>, response: Response<GetProfileResponse>) {
                binding.progressBar.visibility = View.GONE
                binding.loginButton.visibility = View.VISIBLE

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
                        putString("user_pic", user.pic)
                        putString("user_id", user.user_name)
                        apply()
                    }

                    // Save in CurrentUser
                    CurrentUser.setUser(user)

                    // Always request notification permission
                    requestNotificationPermission()

                    // Check if notifications are enabled
                    if (areNotificationsEnabled()) {
                        navigateToHome()
                    } else {
                        showNotificationSnackbar()
                    }
                } else {
                    Log.e("Patrice", "Failed to fetch profile. Response Code: ${response.code()}")
                    Log.e("Patrice", "Error Response Body: ${response.errorBody()?.string()}")
                    Toast.makeText(this@LoginActivity, "Failed to fetch user profile", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GetProfileResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                binding.loginButton.visibility = View.VISIBLE
                Log.e("Patrice", "Error fetching profile: ${t.message}")
                Toast.makeText(this@LoginActivity, "Error fetching profile: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateDeviceToken(authToken: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                Log.d(TAG, "-----------------------> FCM token: $fcmToken")

                val deviceId = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}"

                val deviceTokenRequest = DeviceTokenRequest(fcmToken, deviceId)
                Log.d(TAG, "-----------------------> DEVICE TOKEN REQUEST: ${deviceTokenRequest.device}")
                Log.d(TAG, "-----------------------> DEVICE TOKEN REQUEST: ${deviceTokenRequest.regToken}")

                apiService.updateDeviceToken(deviceTokenRequest).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful) {
                            Log.d("LoginActivity", "Device token updated successfully")
                        } else {
                            Log.e("LoginActivity", "Failed to update device token")
                        }
                    }
                    override fun onFailure(call: Call<String>, t: Throwable) {
                        Log.e("LoginActivity", "Error updating device token: ${t.message}")
                    }
                })
            } else {
                Log.e("LoginActivity", "Error getting FCM token: ${task.exception?.message}")
            }
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            openNotificationSettings()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                navigateToHome()
            } else {
                openNotificationSettings()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun navigateToHome() {
        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showNotificationSnackbar() {
        Snackbar.make(binding.root, "Notifications are required", Snackbar.LENGTH_LONG)
            .setAction("Turn On") { openNotificationSettings() }
            .setActionTextColor(ContextCompat.getColor(this, R.color.accent_color))
            .show()
    }


}
