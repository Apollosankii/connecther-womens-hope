package com.womanglobal.connecther

import android.content.Context
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
import com.bumptech.glide.Glide
import com.womanglobal.connecther.data.SubscriptionPackage
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.databinding.FragmentProfileBinding
import androidx.lifecycle.lifecycleScope
import com.clerk.api.Clerk
import com.womanglobal.connecther.auth.AuthGateActivity
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.CurrentUser
import com.womanglobal.connecther.utils.ServiceBuilder
import kotlinx.coroutines.launch
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
            if (_binding == null || !isAdded) return@let
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
            area_name = null
        )

        // Set user details
        binding.nameText.text = "${user.first_name} ${user.last_name}"
        binding.emailText.text = sharedPreferences.getString("user_email", "No email")

        // Refresh profile from Supabase when Clerk signed in
        val clerkId = AuthGateActivity.getClerkUserId(requireContext()) ?: Clerk.activeUser?.id
        if (clerkId != null) {
            lifecycleScope.launch {
                SupabaseData.getUserProfile(clerkId)?.let { freshUser ->
                    CurrentUser.setUser(freshUser)
                    sharedPreferences.edit()
                        .putString("user_email", freshUser.email ?: "")
                        .putString("user_full_name", "${freshUser.first_name} ${freshUser.last_name}")
                        .putString("user_phone", freshUser.phoneNumber ?: "")
                        .apply()
                    if (_binding != null) {
                        binding.nameText.text = "${freshUser.first_name} ${freshUser.last_name}"
                        binding.emailText.text = freshUser.email?.takeIf { it.isNotBlank() } ?: sharedPreferences.getString("user_email", "No email")
                        Glide.with(this@ProfileFragment)
                            .load(freshUser.pic?.takeIf { it.isNotBlank() } ?: R.drawable.placeholder)
                            .placeholder(R.drawable.placeholder)
                            .error(R.drawable.placeholder)
                            .circleCrop()
                            .into(binding.profilePicture)
                    }
                }
            }
        }

        Glide.with(this)
            .load(user.pic?.takeIf { it.isNotBlank() } ?: R.drawable.placeholder)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder)
            .circleCrop()
            .into(binding.profilePicture)

        // Set up click listener for profile picture change
        binding.profilePicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Apply as service provider: show only when user is not a provider
        val isProvider = sharedPreferences.getBoolean("isProvider", false)
        binding.applyProviderCard.visibility = if (isProvider) View.GONE else View.VISIBLE
        binding.applyProviderButton.setOnClickListener {
            startActivity(Intent(requireContext(), ProviderApplicationActivity::class.java))
        }

        // TabLayout: Account | Subscription
        binding.profileTabLayout.addTab(binding.profileTabLayout.newTab().setText("Account"))
        binding.profileTabLayout.addTab(binding.profileTabLayout.newTab().setText("Subscription"))
        binding.profileTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        binding.accountTabContent.visibility = View.VISIBLE
                        binding.subscriptionTabContent.visibility = View.GONE
                    }
                    1 -> {
                        binding.accountTabContent.visibility = View.GONE
                        binding.subscriptionTabContent.visibility = View.VISIBLE
                        populateSubscriptionPlans()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        // Define the labels and activities for each option
        val optionTags = listOf("Notifications", "Change Password","Payment options", "Terms and policies", "Report a problem", "Settings")
        val optionActivities = listOf(
            NotificationsActivity::class.java,
            PasswordChangeActivity::class.java,
            PaymentOptionsActivity::class.java,
            TermsActivity::class.java,
            ReportProblemActivity::class.java,
            SettingsActivity::class.java
        )

        // Define icons for each option
        val optionIcons = listOf(
            R.mipmap.message,
            R.drawable.baseline_lock_24,
            R.mipmap.clipboard,
            R.mipmap.terms,
            R.mipmap.report,
            R.mipmap.settings
        )

        for (i in 0 until binding.optionsLayout.childCount) {
            val itemView = binding.optionsLayout.getChildAt(i)
            val tag = optionTags[i]
            val activity = optionActivities[i]
            val iconRes = optionIcons[i]

            itemView.findViewById<TextView>(R.id.itemText).text = tag
            itemView.findViewById<ImageView>(R.id.itemIcon).setImageResource(iconRes)

            if (tag == "Notifications") {
                val notificationCount = getNotificationCount()
                val notificationView = itemView.findViewById<TextView>(R.id.notificationCount)
                if (notificationCount > 0) {
                    notificationView.text = notificationCount.toString()
                    notificationView.visibility = View.VISIBLE
                }
            }

            itemView.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), activity))
                }
            }
        }

        addLogoutOption()
    }

    private fun continueOnViewCreated() {
        // TabLayout: Account | Subscription
        binding.profileTabLayout.addTab(binding.profileTabLayout.newTab().setText("Account"))
        binding.profileTabLayout.addTab(binding.profileTabLayout.newTab().setText("Subscription"))
        binding.profileTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        binding.accountTabContent.visibility = View.VISIBLE
                        binding.subscriptionTabContent.visibility = View.GONE
                    }
                    1 -> {
                        binding.accountTabContent.visibility = View.GONE
                        binding.subscriptionTabContent.visibility = View.VISIBLE
                        populateSubscriptionPlans()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        // Define the labels and activities for each option
        val optionTags = listOf("Notifications", "Change Password","Payment options", "Terms and policies", "Report a problem", "Settings")
        val optionActivities = listOf(
            NotificationsActivity::class.java,
            PasswordChangeActivity::class.java,
            PaymentOptionsActivity::class.java,
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
                if (isAdded) {
                    startActivity(Intent(requireContext(), activity))
                }
            }
        }


        // Add Logout Option
        addLogoutOption()
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
        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                val bytes = file.readBytes()
                val url = SupabaseData.uploadProfilePic(bytes, file.name)
                if (url != null) {
                    saveProfileImage(url)
                    Log.d("Upload", "Upload Successful: $url")
                    if (_binding != null) {
                        Glide.with(this@ProfileFragment)
                            .load(url)
                            .placeholder(R.drawable.placeholder)
                            .error(R.drawable.placeholder)
                            .circleCrop()
                            .into(binding.profilePicture)
                    }
                } else {
                    Log.e("Upload", "Supabase upload failed")
                }
            }
        } else {
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

    private fun logoutUser() {
        val activity = requireActivity()

        // Clear local session
        val sharedPreferences = activity.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }

        // Clear Clerk auth prefs (onboarding, clerk_user_id)
        activity.getSharedPreferences("connecther_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // Clear cached Clerk JWT for Supabase (token caching)
        SupabaseClientProvider.clearCachedToken()

        CurrentUser.clear()

        // Mark signed-out so next launch shows sign-in immediately (avoids 5–7s Clerk init wait)
        AuthGateActivity.markSignedOut(activity)

        // Sign out from Clerk (suspend - launch in scope)
        viewLifecycleOwner.lifecycleScope.launch {
            Clerk.auth.signOut()
            val intent = Intent(activity, com.womanglobal.connecther.auth.AuthGateActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity.finish()
        }
    }


    private fun getNotificationCount(): Int {
        // Placeholder for actual logic to fetch the notification count
        return 3
    }

    /** Populates subscription plans from Supabase (or defaults). Fetches when Subscription tab is selected. */
    private fun populateSubscriptionPlans() {
        binding.subscriptionPackagesLayout.removeAllViews()
        lifecycleScope.launch {
            val plans = SupabaseData.getSubscriptionPlans()
            if (_binding == null) return@launch
            for (pkg in plans) {
                val item = layoutInflater.inflate(R.layout.item_subscription_package, binding.subscriptionPackagesLayout, false)
                item.findViewById<TextView>(R.id.packageName).text = pkg.name
                item.findViewById<TextView>(R.id.packageDescription).text = pkg.description
                item.findViewById<TextView>(R.id.packagePrice).text = "KES ${pkg.price}"
                item.findViewById<TextView>(R.id.packageDuration).text = "/ ${pkg.duration}"
                item.findViewById<TextView>(R.id.packageFeatures).text = pkg.features.joinToString("\n") { "• $it" }
                item.findViewById<View>(R.id.popularBadge).visibility = if (pkg.isPopular) View.VISIBLE else View.GONE
                item.findViewById<com.google.android.material.button.MaterialButton>(R.id.subscribeButton).setOnClickListener {
                    android.widget.Toast.makeText(requireContext(), "Subscription coming soon. Contact support.", android.widget.Toast.LENGTH_SHORT).show()
                }
                binding.subscriptionPackagesLayout.addView(item)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
