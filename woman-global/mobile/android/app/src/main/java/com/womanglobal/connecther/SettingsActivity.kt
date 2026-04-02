package com.womanglobal.connecther

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.databinding.ActivitySettingsBinding
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.utils.CurrentUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val titleOptions = arrayOf("Mr", "Mrs", "Miss", "Sir")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val titleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, titleOptions)
        binding.titleInput.setAdapter(titleAdapter)
        binding.titleInput.threshold = 0
        binding.titleInput.keyListener = null
        binding.titleInput.inputType = EditorInfo.TYPE_NULL
        binding.titleInput.setOnClickListener { binding.titleInput.showDropDown() }

        val occupations = occupationChoices()
        val occupationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, occupations)
        binding.occupationInput.setAdapter(occupationAdapter)
        binding.occupationInput.threshold = 0
        binding.occupationInput.keyListener = null
        binding.occupationInput.inputType = EditorInfo.TYPE_NULL
        binding.occupationInput.setOnClickListener { binding.occupationInput.showDropDown() }

        val otherLabel = getString(R.string.settings_occupation_other)
        binding.occupationInput.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position).toString()
            if (selected == otherLabel) {
                binding.customOccupationInputLayout.visibility = View.VISIBLE
            } else {
                binding.customOccupationInputLayout.visibility = View.GONE
                binding.customOccupationEditText.setText("")
            }
        }

        binding.saveButton.setOnClickListener { saveUserInfo() }

        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        applyUserToForm(resolveUser(prefs), prefs)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val uid = prefs.getString("firebase_uid", null) ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.ensureSupabaseSession()
                SupabaseData.syncLocalProfileFromSupabase(uid, prefs)
            }
            if (!isFinishing) {
                applyUserToForm(resolveUser(prefs), prefs)
            }
        }
    }

    private fun occupationChoices(): List<String> {
        val other = getString(R.string.settings_occupation_other)
        return loadCategories() + other
    }

    private fun loadCategories(): List<String> =
        listOf("Housekeeper", "Gardener", "Chef", "Driver", "Nanny")

    private fun resolveUser(prefs: SharedPreferences): User {
        return CurrentUser.getUser() ?: User(
            id = prefs.getString("user_id", "") ?: "",
            first_name = prefs.getString("user_full_name", "User Name") ?: "",
            last_name = "",
            phoneNumber = prefs.getString("user_phone", "No phone number"),
            pic = prefs.getString("user_pic", null),
            isIdVerified = false,
            isMobileVerified = false,
            details = "",
            occupation = "",
            nat_id = null,
            dob = null,
            gender = null,
            user_name = "",
            title = prefs.getString("user_title", null),
            country = null,
            county = null,
            area_name = null,
            email = prefs.getString("user_email", null),
        )
    }

    private fun displayFullName(user: User, prefs: SharedPreferences): String {
        val combined = "${user.first_name} ${user.last_name}".trim()
        if (combined.isNotBlank()) return combined
        return prefs.getString("user_full_name", "")?.trim().orEmpty()
    }

    private fun applyUserToForm(user: User, prefs: SharedPreferences) {
        binding.fullNameEditText.setText(displayFullName(user, prefs))
        binding.emailEditText.setText(
            user.email?.takeIf { it.isNotBlank() }
                ?: prefs.getString("user_email", "")?.takeIf { it.isNotBlank() && it != "No Email" }
                ?: "",
        )
        val rawPhone = user.phoneNumber?.takeIf { it.isNotBlank() && it != "No phone number" }
            ?: prefs.getString("user_phone", "")?.takeIf { it != "No phone number" }
        binding.phoneEditText.setText(rawPhone ?: "")

        val currentTitle = user.title?.trim().orEmpty()
        if (currentTitle.isNotEmpty() && titleOptions.contains(currentTitle)) {
            binding.titleInput.setText(currentTitle, false)
        }

        val occ = user.occupation?.trim().orEmpty()
        val choices = occupationChoices()
        val otherLabel = getString(R.string.settings_occupation_other)
        when {
            occ.isEmpty() -> {
                binding.occupationInput.setText("", false)
                binding.customOccupationInputLayout.visibility = View.GONE
            }
            choices.contains(occ) -> {
                binding.occupationInput.setText(occ, false)
                binding.customOccupationInputLayout.visibility =
                    if (occ == otherLabel) View.VISIBLE else View.GONE
                if (occ != otherLabel) binding.customOccupationEditText.setText("")
            }
            else -> {
                binding.occupationInput.setText(otherLabel, false)
                binding.customOccupationInputLayout.visibility = View.VISIBLE
                binding.customOccupationEditText.setText(occ)
            }
        }

        Glide.with(this)
            .load(user.pic)
            .placeholder(R.drawable.ic_avatar_neutral)
            .error(R.drawable.ic_avatar_neutral)
            .circleCrop()
            .into(binding.settingsAvatar)
    }

    private fun clearFieldErrors() {
        binding.titleInputLayout.error = null
        binding.fullNameInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.phoneInputLayout.error = null
        binding.occupationInputLayout.error = null
        binding.customOccupationInputLayout.error = null
    }

    private fun saveUserInfo() {
        clearFieldErrors()
        val fullName = binding.fullNameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val title = binding.titleInput.text.toString().trim()
        val otherLabel = getString(R.string.settings_occupation_other)
        val occSelection = binding.occupationInput.text.toString().trim()
        val occupation = when {
            occSelection.isEmpty() -> ""
            occSelection == otherLabel -> binding.customOccupationEditText.text.toString().trim()
            else -> occSelection
        }

        if (fullName.isEmpty()) {
            binding.fullNameInputLayout.error = getString(R.string.settings_error_full_name)
            return
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.login_error_email)
            return
        }
        if (occSelection == otherLabel && occupation.isEmpty()) {
            binding.customOccupationInputLayout.error = getString(R.string.settings_error_occupation_custom)
            return
        }

        setLoading(true)
        val nameParts = fullName.split(" ", limit = 2)
        val first = nameParts.getOrElse(0) { "" }
        val last = nameParts.getOrElse(1) { "" }
        val titleForApi = title.ifBlank { null }
        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val accountEmailHint = sequenceOf(
            resolveUser(prefs).email,
            prefs.getString("user_email", null),
            FirebaseAuth.getInstance().currentUser?.email,
            CurrentUser.getUser()?.email,
        ).mapNotNull { it?.trim()?.takeIf { e -> e.isNotEmpty() && e != "No Email" } }
            .firstOrNull()

        lifecycleScope.launch {
            val errDetail = SupabaseData.updateUserProfileResult(
                first, last, phone, email, occupation,
                title = titleForApi,
                accountEmailHint = accountEmailHint,
            )
            if (!isFinishing) {
                setLoading(false)
                if (errDetail == null) {
                    persistLocalProfile(fullName, email, phone, occupation, titleForApi)
                    Toast.makeText(this@SettingsActivity, R.string.settings_updated, Toast.LENGTH_SHORT).show()
                } else {
                    val msg = SupabaseData.profileUpdateUserMessage(this@SettingsActivity, errDetail)
                    Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun persistLocalProfile(
        fullName: String,
        email: String,
        phone: String,
        occupation: String,
        title: String?,
    ) {
        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_full_name", fullName)
            putString("user_email", email)
            putString("user_phone", phone.ifBlank { "" })
            if (title.isNullOrBlank()) remove("user_title") else putString("user_title", title)
            apply()
        }

        val parts = fullName.trim().split(Regex("\\s+"), limit = 2)
        val first = parts.getOrElse(0) { "" }
        val last = parts.getOrElse(1) { "" }
        val existing = CurrentUser.getUser()
        if (existing != null) {
            CurrentUser.setUser(
                existing.copy(
                    first_name = first,
                    last_name = last,
                    email = email,
                    phoneNumber = phone.ifBlank { null },
                    occupation = occupation.ifBlank { null },
                    title = title,
                ),
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.saveButton.isEnabled = !loading
    }
}
