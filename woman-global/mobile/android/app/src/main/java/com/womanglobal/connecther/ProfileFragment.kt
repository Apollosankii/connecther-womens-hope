package com.womanglobal.connecther

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.Glide
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.databinding.FragmentProfileBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.utils.CurrentUser
import com.womanglobal.connecther.utils.ServiceBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val apiService: ApiService by lazy { ServiceBuilder.buildService(ApiService::class.java) }


    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Glide.with(this)
                .load(it)
                .circleCrop()
                .into(binding.profilePicture)

            // Convert Uri to File and upload
            val file = getFileFromUri(requireContext(), it)
            if (file != null) {
                uploadImage(file)
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val sharedPreferences = requireActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        applyUserToProfileViews(resolveDisplayUser(sharedPreferences), sharedPreferences)

        // Set up click listener for profile picture change
        binding.profilePicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }


        // Define the labels and activities for each option
        val optionTags = listOf("Notifications", "Change Password","Subscriptions", "Terms and policies", "Report a problem", "Settings")
        val optionActivities = listOf(
            NotificationsActivity::class.java,
            PasswordChangeActivity::class.java,
            SubscriptionsActivity::class.java,
            TermsActivity::class.java,
            ReportProblemActivity::class.java,
            SettingsActivity::class.java
        )

        // Define icons for each option
        val optionIcons = listOf(
            R.mipmap.message,
            R.drawable.baseline_lock_24,// Notifications icon
            R.mipmap.clipboard,               // Payment options icon
            R.mipmap.terms,                // Terms and policies icon
            R.mipmap.report,         // Report a problem icon
            R.mipmap.settings         // Settings icon
        )

        for (i in 0 until binding.optionsLayout.childCount) {
            val itemView = binding.optionsLayout.getChildAt(i)
            val tag = optionTags[i]
            val activity = optionActivities[i]
            val iconRes = optionIcons[i]

            // Set label and icon for each option
            itemView.findViewById<TextView>(R.id.itemText).text = tag
            itemView.findViewById<ImageView>(R.id.itemIcon).setImageResource(iconRes)

            // Set notification count if this is the Notifications option
            if (tag == "Notifications") {
                val notificationCount = getNotificationCount()
                val notificationView = itemView.findViewById<TextView>(R.id.notificationCount)
                if (notificationCount > 0) {
                    notificationView.text = notificationCount.toString()
                    notificationView.visibility = View.VISIBLE
                }
            }

            // Set click listener to open the appropriate activity
            itemView.setOnClickListener {
                val intent = Intent(getActivity(), activity)
                startActivity(intent)
            }
        }

        addProfileOption("Provider Profile", R.drawable.ic_profile_nav) {
            startActivity(Intent(requireContext(), ProviderProfileActivity::class.java))
        }
        addProfileOption("Provider Application", R.drawable.baseline_work_outline_24) {
            startActivity(Intent(requireContext(), ProviderApplicationActivity::class.java))
        }


        // Add Logout Option
        addLogoutOption()
    }

    override fun onResume() {
        super.onResume()
        val prefs = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val uid = prefs.getString("firebase_uid", null) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.ensureSupabaseSession()
                SupabaseData.syncLocalProfileFromSupabase(uid, prefs)
            }
            if (_binding == null) return@launch
            applyUserToProfileViews(resolveDisplayUser(prefs), prefs)
        }
    }

    private fun resolveDisplayUser(prefs: SharedPreferences): User {
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
            title = null,
            country = null,
            county = null,
            area_name = null
        )
    }

    private fun applyUserToProfileViews(user: User, prefs: SharedPreferences) {
        val displayName = "${user.first_name} ${user.last_name}".trim()
            .ifBlank { prefs.getString("user_full_name", "User") ?: "User" }
        binding.nameText.text = displayName
        binding.emailText.text = prefs.getString("user_email", "No email")
        Log.d("PROFILE", "------------------> phone ${user.phoneNumber}")

        Glide.with(this)
            .load(user.pic)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder)
            .circleCrop()
            .into(binding.profilePicture)
    }

    fun getFileFromUri(context: Context, uri: Uri): File? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)

        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }


    private fun uploadImage(file: File) {
        val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("profileImage", file.name, requestFile)

        apiService.uploadProfilePic(body).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    val imageUrl = response.body()
                    saveProfileImage(imageUrl ?: "")
                    Log.d("Upload", "Upload Successful: $imageUrl")
                } else {
                    Log.e("Upload", "Upload failed: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Log.e("Upload", "Error: ${t.message}")
            }
        })
    }

    private fun saveProfileImage(imageUri: String) {
        val sharedPreferences = requireActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("user_pic", imageUri)
            apply()
        }
    }

    private fun addLogoutOption() {
        val logoutItem = layoutInflater.inflate(R.layout.item_profile_option, binding.optionsLayout, false)
        logoutItem.findViewById<TextView>(R.id.itemText).text = "Logout"
        logoutItem.findViewById<ImageView>(R.id.itemIcon).setImageResource(R.drawable.baseline_close_24)

        logoutItem.setOnClickListener {
            logoutUser()
        }

        binding.optionsLayout.addView(logoutItem)
    }

    private fun addProfileOption(label: String, iconRes: Int, onClick: () -> Unit) {
        val item = layoutInflater.inflate(R.layout.item_profile_option, binding.optionsLayout, false)
        item.findViewById<TextView>(R.id.itemText).text = label
        item.findViewById<ImageView>(R.id.itemIcon).setImageResource(iconRes)
        item.findViewById<TextView>(R.id.notificationCount).visibility = View.GONE
        item.setOnClickListener { onClick() }
        binding.optionsLayout.addView(item)
    }

    private fun logoutUser() {
        val sharedPreferences = requireActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            clear() // Clears all user data
            apply()
        }

        // Clear CurrentUser
        CurrentUser.clear()

        // Redirect to LoginActivity
        val intent = Intent(requireActivity(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }


    private fun getNotificationCount(): Int {
        // Placeholder for actual logic to fetch the notification count
        return 3
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}
