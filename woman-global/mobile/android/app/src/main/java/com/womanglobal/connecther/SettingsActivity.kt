// SettingsActivity.kt
package com.womanglobal.connecther

import ApiServiceFactory
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.services.UpdateUserRequest
import com.womanglobal.connecther.databinding.ActivitySettingsBinding
import com.womanglobal.connecther.utils.CurrentUser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val apiService = ApiServiceFactory.createApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prepopulate fields with existing user data if available
        loadUserData()

        // Populate occupation spinner with categories + "Other" option
        val categories = listOf("Select Occupation") + loadCategories() + "Other"
        setupOccupationSpinner(categories)

        // Set up save button click listener
        binding.saveButton.setOnClickListener { saveUserInfo() }
    }

    private fun loadUserData() {
        // Retrieve existing data from shared preferences or database
        val sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        // Get user info
        val user = CurrentUser.getUser() ?: User(
            id = sharedPreferences.getString("user_id", "") ?: "",
            first_name = sharedPreferences.getString("user_full_name", "User Name") ?: "",
            last_name = "",
            phoneNumber = sharedPreferences.getString("user_phone", "No phone number"),
            pic = sharedPreferences.getString("user_pic", null),
            isIdVerified = false,
            isMobileVerified = false,
            details = "",
            occupation = "",
            nat_id = null,
            dob = null,
            gender = null,
            user_name = "",
            title = null,
            country = null,
            county = null,
            area_name = null,
        )
        val phone = sharedPreferences.getString("user_phone", "No phone number")
        val email = sharedPreferences.getString("user_email", "No Email")
        Log.d("UserData", "--------------------> Phone number from SharedPreferences: $phone")
        binding.phoneEditText.setText(phone)

        // Replace with actual data retrieval logic
        // Populate UI fields
        binding.fullNameEditText.setText("${user.first_name} ${user.last_name}")
        binding.emailEditText.setText(email)
//        binding.phoneEditText.setText(user.phoneNumber ?: "No phone number")
        // Optionally set occupation in spinner if known
    }

    private fun loadCategories(): List<String> {
        return listOf("Housekeeper", "Gardener", "Chef", "Driver", "Nanny")
    }

    private fun setupOccupationSpinner(occupations: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, occupations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.occupationSpinner.adapter = adapter

        binding.occupationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (occupations[position] == "Other") {
                    binding.customOccupationEditText.visibility = View.VISIBLE
                } else {
                    binding.customOccupationEditText.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun saveUserInfo() {
        val fullName = binding.fullNameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val occupation = if (binding.occupationSpinner.selectedItem == "Other") {
            binding.customOccupationEditText.text.toString().trim()
        } else {
            binding.occupationSpinner.selectedItem.toString()
        }

// Create request body object
        val updateUserRequest = UpdateUserRequest(fullName, email, phone, occupation)

// Make API request
        apiService.updateUserInfo(updateUserRequest).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@SettingsActivity, "User info updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Failed to update user info: ${response.errorBody()?.string()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@SettingsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })

    }
}
