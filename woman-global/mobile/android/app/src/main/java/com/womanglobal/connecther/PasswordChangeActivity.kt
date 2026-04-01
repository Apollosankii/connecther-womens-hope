package com.womanglobal.connecther

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.databinding.ActivityPasswordChangeBinding

class PasswordChangeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordChangeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.savePasswordButton.setOnClickListener {
            val currentPassword = binding.currentPasswordInput.text.toString().trim()
            val newPassword = binding.newPasswordInput.text.toString().trim()
            val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

            binding.currentPasswordLayout.error = null
            binding.newPasswordLayout.error = null
            binding.confirmPasswordLayout.error = null

            if (currentPassword.isEmpty()) {
                binding.currentPasswordLayout.error = getString(R.string.password_change_current_required)
                return@setOnClickListener
            }
            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                showToast(getString(R.string.password_change_fill_both))
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                binding.confirmPasswordLayout.error = getString(R.string.password_change_mismatch)
                return@setOnClickListener
            }
            if (newPassword.length < 6) {
                binding.newPasswordLayout.error = getString(R.string.password_change_too_short)
                return@setOnClickListener
            }

            updatePassword(currentPassword, newPassword)
        }
    }

    private fun updatePassword(currentPassword: String, newPassword: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            showToast(getString(R.string.password_change_sign_in_first))
            return
        }
        val email = user.email
        if (email.isNullOrBlank()) {
            showToast(getString(R.string.password_change_no_email))
            return
        }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).addOnCompleteListener { reauth ->
            if (reauth.isSuccessful) {
                user.updatePassword(newPassword).addOnCompleteListener { update ->
                    if (update.isSuccessful) {
                        showToast(getString(R.string.password_change_success))
                        finish()
                    } else {
                        showToast(
                            getString(
                                R.string.password_change_failed,
                                update.exception?.localizedMessage ?: "",
                            ),
                        )
                    }
                }
            } else {
                binding.currentPasswordLayout.error = getString(R.string.password_change_wrong_current)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
