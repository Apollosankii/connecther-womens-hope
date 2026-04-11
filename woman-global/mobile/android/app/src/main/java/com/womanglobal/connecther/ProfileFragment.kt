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
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.databinding.FragmentProfileBinding
import com.womanglobal.connecther.utils.CurrentUser
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var pendingCameraUri: Uri? = null
    private val takeProfilePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (!ok || uri == null) return@registerForActivityResult
        uri.let {
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
            val photoFile = File.createTempFile("profile_", ".jpg", requireContext().cacheDir)
            val outUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile,
            )
            pendingCameraUri = outUri
            takeProfilePhotoLauncher.launch(outUri)
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

        val prefs = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val isProvider = prefs.getBoolean("isProvider", false)
        val isPending = prefs.getBoolean("isProviderPending", false)
        if (isProvider) {
            addProfileOption("Provider Profile", R.drawable.ic_profile_nav) {
                startActivity(Intent(requireContext(), ProviderProfileActivity::class.java))
            }
        } else if (isPending) {
            addProfileOption("Application Pending", R.drawable.baseline_work_outline_24) {}
        } else {
            addProfileOption("Become a Provider", R.drawable.baseline_work_outline_24) {
                startActivity(Intent(requireContext(), ProviderApplicationActivity::class.java))
            }
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
        val prefPic = prefs.getString("user_pic", null)
        val prefName = prefs.getString("user_full_name", null)?.trim().orEmpty()
        val prefPhone = prefs.getString("user_phone", null)
        val prefEmail = prefs.getString("user_email", null)
        val fromPrefsFallback = User(
            id = prefs.getString("user_id", "") ?: "",
            first_name = prefName.ifBlank { "User Name" },
            last_name = "",
            phoneNumber = prefPhone ?: "No phone number",
            pic = prefPic,
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
            email = prefEmail,
        )
        val mem = CurrentUser.getUser() ?: return fromPrefsFallback
        // Prefs are refreshed from Supabase in onResume; prefer them over stale Gson-cached User.
        val nameParts = prefName.split(" ", limit = 2)
        val mergedFirst = if (prefName.isNotBlank()) nameParts[0] else mem.first_name
        val mergedLast = if (prefName.isNotBlank() && nameParts.size > 1) nameParts[1] else mem.last_name
        return mem.copy(
            first_name = mergedFirst,
            last_name = mergedLast,
            pic = prefPic ?: mem.pic,
            phoneNumber = prefPhone ?: mem.phoneNumber,
            email = prefEmail ?: mem.email,
        )
    }

    private fun applyUserToProfileViews(user: User, prefs: SharedPreferences) {
        val displayName = "${user.first_name} ${user.last_name}".trim()
            .ifBlank { prefs.getString("user_full_name", "User") ?: "User" }
        binding.nameText.text = displayName
        binding.emailText.text = prefs.getString("user_email", "No email")
        if (com.womanglobal.connecther.BuildConfig.DEBUG) {
            Log.d("PROFILE", "phone loaded: ${user.phoneNumber?.take(3)}***")
        }

        Glide.with(this)
            .load(user.pic)
            .signature(ObjectKey(prefs.getLong("profile_pic_version", 0L)))
            .placeholder(R.drawable.ic_avatar_neutral)
            .error(R.drawable.ic_avatar_neutral)
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
        val prefs = requireActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val firebaseUid = prefs.getString("firebase_uid", null)
        if (firebaseUid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            runCatching { file.delete() }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val sessionOk = withContext(Dispatchers.IO) { SupabaseClientProvider.ensureSupabaseSession() }
            if (!sessionOk) {
                Toast.makeText(requireContext(), "Session expired. Sign in again.", Toast.LENGTH_LONG).show()
                runCatching { file.delete() }
                return@launch
            }
            val url = withContext(Dispatchers.IO) {
                runCatching {
                    SupabaseData.uploadProfilePic(file.readBytes(), file.name)
                }.onFailure { e ->
                    Log.e("ProfileFragment", "uploadProfilePic", e)
                }.getOrNull()
            }
            runCatching { file.delete() }
            if (url != null) {
                prefs.edit()
                    .putString("user_pic", url)
                    .putLong("profile_pic_version", System.currentTimeMillis())
                    .apply()
                CurrentUser.getUser()?.let { u -> CurrentUser.setUser(u.copy(pic = url)) }
                withContext(Dispatchers.IO) {
                    SupabaseData.syncLocalProfileFromSupabase(firebaseUid, prefs)
                }
                if (_binding != null) {
                    applyUserToProfileViews(resolveDisplayUser(prefs), prefs)
                }
                Toast.makeText(requireContext(), "Profile photo saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Could not upload photo. Check your connection and that Storage is set up (profpics bucket).",
                    Toast.LENGTH_LONG,
                ).show()
            }
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
        return 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}
