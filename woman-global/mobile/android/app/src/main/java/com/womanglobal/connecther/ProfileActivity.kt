package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView
    private lateinit var userName: TextView
    private lateinit var profileServiceSubtitle: TextView
    private lateinit var profileImage: ImageView
    private lateinit var idVerifiedIcon: ImageView
    private lateinit var idVerifiedText: TextView
    private lateinit var phoneVerifiedIcon: ImageView
    private lateinit var phoneVerifiedText: TextView
    private lateinit var valueProfessionalTitle: TextView
    private lateinit var valueServiceCategory: TextView
    private lateinit var valueLocation: TextView
    private lateinit var valueExperience: TextView
    private lateinit var labelWorkingHours: TextView
    private lateinit var valueWorkingHours: TextView
    private lateinit var buttonMessageProvider: MaterialButton
    private lateinit var recommendButton: Button
    private lateinit var engageButton: Button
    private lateinit var profileSeekerExtrasSection: View
    private lateinit var profileRatingSummary: TextView
    private lateinit var profileReviewsBody: TextView
    private lateinit var profileDocumentsList: LinearLayout
    private lateinit var profileDocumentsMessage: TextView
    private lateinit var profileBusyBanner: View

    private lateinit var providerRef: String
    private lateinit var providerName: String
    private lateinit var serviceId: String
    private var serviceNameFromIntent: String = ""
    private var profileUserForBookability: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        backButton = findViewById(R.id.backButton)
        userName = findViewById(R.id.userName)
        profileServiceSubtitle = findViewById(R.id.profileServiceSubtitle)
        profileImage = findViewById(R.id.profileImage)
        idVerifiedIcon = findViewById(R.id.idVerifiedIcon)
        idVerifiedText = findViewById(R.id.idVerifiedText)
        phoneVerifiedIcon = findViewById(R.id.phoneVerifiedIcon)
        phoneVerifiedText = findViewById(R.id.phoneVerifiedText)
        valueProfessionalTitle = findViewById(R.id.valueProfessionalTitle)
        valueServiceCategory = findViewById(R.id.valueServiceCategory)
        valueLocation = findViewById(R.id.valueLocation)
        valueExperience = findViewById(R.id.valueExperience)
        labelWorkingHours = findViewById(R.id.labelWorkingHours)
        valueWorkingHours = findViewById(R.id.valueWorkingHours)
        buttonMessageProvider = findViewById(R.id.buttonMessageProvider)
        recommendButton = findViewById(R.id.recommendButton)
        engageButton = findViewById(R.id.engageButton)
        profileSeekerExtrasSection = findViewById(R.id.profileSeekerExtrasSection)
        profileRatingSummary = findViewById(R.id.profileRatingSummary)
        profileReviewsBody = findViewById(R.id.profileReviewsBody)
        profileDocumentsList = findViewById(R.id.profileDocumentsList)
        profileDocumentsMessage = findViewById(R.id.profileDocumentsMessage)
        profileBusyBanner = findViewById(R.id.profileBusyBanner)

        val user = intent.getSerializableExtra("user") as? User
        providerRef = user?.user_name?.takeIf { it.isNotBlank() }
            ?: user?.id?.takeIf { it.isNotBlank() }
            ?: ""
        providerName = "${user?.first_name.orEmpty()} ${user?.last_name.orEmpty()}".trim()
            .ifBlank { getString(R.string.profile_not_specified) }

        serviceId = intent.getStringExtra("service_id").orEmpty()
        serviceNameFromIntent = intent.getStringExtra("service_name").orEmpty()

        user?.let { bindUser(it) } ?: run {
            Toast.makeText(this, R.string.profile_provider_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        backButton.setOnClickListener { finish() }

        buttonMessageProvider.setOnClickListener { openMessageWithProvider() }

        engageButton.setOnClickListener { engageWithProvider() }

        recommendButton.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Hey, check out this app! Download it here:")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }
    }

    private fun bindUser(user: User) {
        profileUserForBookability = user
        userName.text = providerName

        if (serviceNameFromIntent.isNotBlank()) {
            profileServiceSubtitle.visibility = View.VISIBLE
            profileServiceSubtitle.text = getString(R.string.profile_browsing_service, serviceNameFromIntent)
        } else {
            profileServiceSubtitle.visibility = View.GONE
        }

        val picUrl = user.pic?.trim()?.takeIf { it.isNotEmpty() }
        if (picUrl != null) {
            Glide.with(this)
                .load(picUrl)
                .placeholder(R.drawable.ic_avatar_neutral)
                .centerInside()
                .into(profileImage)
        } else {
            Glide.with(this).clear(profileImage)
            profileImage.setImageResource(R.drawable.ic_avatar_neutral)
        }

        val showId = user.isIdVerified == true
        idVerifiedIcon.visibility = if (showId) View.VISIBLE else View.GONE
        idVerifiedText.visibility = if (showId) View.VISIBLE else View.GONE

        val showPhone = user.isMobileVerified == true
        phoneVerifiedIcon.visibility = if (showPhone) View.VISIBLE else View.GONE
        phoneVerifiedText.visibility = if (showPhone) View.VISIBLE else View.GONE

        val titleText = user.professionalTitle?.takeIf { it.isNotBlank() }
            ?: user.title?.takeIf { it.isNotBlank() }
        valueProfessionalTitle.text = titleText ?: getString(R.string.profile_not_specified)

        valueServiceCategory.text = serviceNameFromIntent.takeIf { it.isNotBlank() }
            ?: getString(R.string.profile_not_specified)

        val locationParts = listOfNotNull(
            user.area_name?.takeIf { it.isNotBlank() },
            user.county?.takeIf { it.isNotBlank() },
            user.country?.takeIf { it.isNotBlank() },
        )
        valueLocation.text = if (locationParts.isNotEmpty()) {
            locationParts.joinToString(", ")
        } else {
            getString(R.string.profile_not_specified)
        }

        val experience = user.occupation?.takeIf { it.isNotBlank() }
            ?: user.details?.takeIf { it.isNotBlank() }
        valueExperience.text = experience ?: getString(R.string.profile_no_experience_yet)

        val hours = user.workingHours?.trim().orEmpty()
        if (hours.isNotEmpty()) {
            labelWorkingHours.visibility = View.VISIBLE
            valueWorkingHours.visibility = View.VISIBLE
            valueWorkingHours.text = hours
        } else {
            labelWorkingHours.visibility = View.GONE
            valueWorkingHours.visibility = View.GONE
        }

        loadProviderPublicReviews(user)
        loadProviderBookability(user)
    }

    /** Marketplace listing uses the same rules: not bookable while an active (incomplete) job exists. */
    private fun loadProviderBookability(user: User) {
        val uid = user.userDbId
        if (user.isServiceProvider != true || uid == null) {
            profileBusyBanner.visibility = View.GONE
            setEngageBookable(true)
            return
        }
        profileBusyBanner.visibility = View.GONE
        setEngageBookable(true)
        lifecycleScope.launch {
            val bookable = SupabaseData.providerIsBookable(uid)
            if (bookable) {
                profileBusyBanner.visibility = View.GONE
                setEngageBookable(true)
            } else {
                profileBusyBanner.visibility = View.VISIBLE
                setEngageBookable(false)
            }
        }
    }

    private fun setEngageBookable(bookable: Boolean) {
        engageButton.isEnabled = bookable
        engageButton.alpha = if (bookable) 1f else 0.45f
    }

    /**
     * Public reviews (seeker → provider, [job_reviews.is_public]) and portfolio docs live under
     * Experience & skills so seekers see them in one place.
     */
    private fun loadProviderPublicReviews(user: User) {
        val uid = user.userDbId
        if (user.isServiceProvider != true || uid == null) {
            profileSeekerExtrasSection.visibility = View.GONE
            return
        }
        profileSeekerExtrasSection.visibility = View.VISIBLE
        profileRatingSummary.text = getString(R.string.profile_reviews_empty)
        profileReviewsBody.text = ""
        profileDocumentsMessage.visibility = View.GONE
        profileDocumentsList.removeAllViews()

        lifecycleScope.launch {
            val stats = SupabaseData.getPublicRatingStatsForUser(uid)
            val reviews = SupabaseData.listPublicReviewsForUser(uid, 25)
            val count = stats?.second ?: 0
            val avg = stats?.first ?: 0.0
            if (count == 0 && reviews.isEmpty()) {
                profileRatingSummary.text = getString(R.string.profile_reviews_empty)
            } else {
                profileRatingSummary.text = getString(R.string.profile_reviews_average, avg, count)
                profileReviewsBody.text = if (reviews.isEmpty()) {
                    getString(R.string.profile_reviews_empty)
                } else {
                    reviews.joinToString("\n\n") { r ->
                        getString(
                            R.string.profile_review_line,
                            r.stars,
                            r.reviewerFirstName.ifBlank { "—" },
                            r.serviceName.ifBlank { "—" },
                            r.reviewText.ifBlank { "" },
                        )
                    }
                }
            }

            if (!SupabaseClientProvider.ensureSupabaseSession()) {
                profileDocumentsMessage.visibility = View.VISIBLE
                profileDocumentsMessage.text = getString(R.string.profile_documents_sign_in)
                return@launch
            }
            val docs = SupabaseData.listProviderPortfolioDocuments(uid)
            if (docs.isEmpty()) {
                profileDocumentsMessage.visibility = View.VISIBLE
                profileDocumentsMessage.text = getString(R.string.profile_documents_empty)
            } else {
                profileDocumentsMessage.visibility = View.GONE
                val gap = (8 * resources.displayMetrics.density).toInt()
                docs.forEach { doc ->
                    val btn = MaterialButton(
                        this@ProfileActivity,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = gap }
                        text = buildString {
                            append(getString(R.string.profile_documents_open, doc.docTypeName))
                            if (doc.verified) {
                                append(" · ")
                                append(getString(R.string.profile_documents_verified))
                            }
                        }
                        isEnabled = !doc.signedUrl.isNullOrBlank()
                        setOnClickListener {
                            val url = doc.signedUrl
                            if (url.isNullOrBlank()) {
                                Toast.makeText(
                                    this@ProfileActivity,
                                    R.string.profile_documents_unavailable,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                startActivity(
                                    Intent(this@ProfileActivity, SecureProviderDocumentActivity::class.java).apply {
                                        putExtra(SecureProviderDocumentActivity.EXTRA_URL, url)
                                        putExtra(
                                            SecureProviderDocumentActivity.EXTRA_TITLE,
                                            doc.docTypeName,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    profileDocumentsList.addView(btn)
                }
            }
        }
    }

    private fun openMessageWithProvider() {
        if (providerRef.isBlank()) {
            Toast.makeText(this, R.string.profile_provider_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val sid = serviceId.toIntOrNull()
        if (sid == null) {
            Toast.makeText(this, R.string.profile_chat_failed, Toast.LENGTH_SHORT).show()
            return
        }
        buttonMessageProvider.isEnabled = false
        lifecycleScope.launch {
            val outcome = SupabaseData.startConversationWithProvider(providerRef, sid)
            buttonMessageProvider.isEnabled = true
            val chatCode = outcome.chatCode?.takeIf { it.isNotBlank() }
            val err = outcome.errorCode
            when {
                chatCode != null -> {
                    startActivity(
                        Intent(this@ProfileActivity, ChatActivity::class.java).apply {
                            putExtra("chat_code", chatCode)
                            outcome.quoteId?.takeIf { it.isNotBlank() }?.let {
                                putExtra("quote_id", it)
                            }
                            putExtra("peer_display_name", providerName)
                            putExtra("providerName", providerName)
                            (intent.getSerializableExtra("user") as? User)?.pic
                                ?.takeIf { it.isNotBlank() }
                                ?.let { putExtra("peer_pic", it) }
                            putExtra(
                                "serviceName",
                                serviceNameFromIntent.ifBlank { valueServiceCategory.text.toString() },
                            )
                        },
                    )
                }
                err == "not_authenticated" || err == "auth_required" ->
                    Toast.makeText(this@ProfileActivity, R.string.profile_message_sign_in, Toast.LENGTH_LONG).show()
                err == "provider_not_found" ->
                    Toast.makeText(this@ProfileActivity, R.string.profile_provider_not_found, Toast.LENGTH_SHORT).show()
                err == "cannot_chat_with_self" ->
                    Toast.makeText(this@ProfileActivity, R.string.profile_chat_self, Toast.LENGTH_LONG).show()
                err == "network_timeout" ->
                    Toast.makeText(this@ProfileActivity, R.string.network_request_timeout, Toast.LENGTH_LONG).show()
                else ->
                    Toast.makeText(this@ProfileActivity, R.string.profile_chat_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun engageWithProvider() {
        if (!engageButton.isEnabled) {
            Toast.makeText(this, R.string.profile_engagement_blocked_busy, Toast.LENGTH_LONG).show()
            return
        }
        if (providerRef.isBlank() || serviceId.isBlank()) {
            Toast.makeText(this, "Provider/service details are missing", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, RequestBookingActivity::class.java).apply {
            putExtra("provider_ref", providerRef)
            putExtra("service_id", serviceId)
            putExtra("provider_name", providerName)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        profileUserForBookability?.let { loadProviderBookability(it) }
    }
}
