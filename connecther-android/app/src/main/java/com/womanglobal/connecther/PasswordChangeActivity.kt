package com.womanglobal.connecther

import ApiServiceFactory
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.ChangePasswordRequest
import com.womanglobal.connecther.utils.FirebaseHelper
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PasswordChangeActivity : AppCompatActivity() {
    private lateinit var apiService: ApiService

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_password_change)

        apiService = ApiServiceFactory.createApiService() // Initialize the API service

        val currentPasswordInput = findViewById<EditText>(R.id.currentPasswordInput)
        val newPasswordInput = findViewById<EditText>(R.id.newPasswordInput)
        val confirmPasswordInput = findViewById<EditText>(R.id.confirmPasswordInput)
        val savePasswordButton = findViewById<Button>(R.id.savePasswordButton)

        savePasswordButton.setOnClickListener {
            val currentPassword = currentPasswordInput.text.toString().trim()
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                showToast("New password and confirm password cannot be empty")
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                showToast("New password and confirm password do not match")
                return@setOnClickListener
            }

            if (newPassword.length < 6) {
                showToast("Password should be at least 6 characters long")
                return@setOnClickListener
            }

            updatePassword(currentPassword, newPassword, confirmPassword)
        }
    }

    private fun updatePassword(old: String, new: String, confirm: String) {
        val changePasswordRequest = ChangePasswordRequest(old, new, confirm)

        apiService.changePassword(changePasswordRequest).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful && response.body() == "Password Updated") {
                    showToast("${response.body()} successfully")
                    val intent = Intent(this@PasswordChangeActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    showToast("Failed to update password: ${response.errorBody()?.string()}")
                }
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                showToast("Error: ${t.localizedMessage}")
            }
        })
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
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
}
