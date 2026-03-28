package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.womanglobal.connecther.data.User

class ProfileActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView
    private lateinit var userName: TextView
    private lateinit var profileImage: ImageView
    private lateinit var idVerifiedIcon: ImageView
    private lateinit var idVerifiedText: TextView
    private lateinit var phoneVerifiedIcon: ImageView
    private lateinit var phoneVerifiedText: TextView
    private lateinit var skillsText: TextView
    private lateinit var experienceText: TextView
    private lateinit var recommendButton: Button
    private lateinit var engageButton: Button

    private lateinit var providerRef: String
    private lateinit var providerName: String
    private lateinit var serviceId: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize views
        backButton = findViewById(R.id.backButton)
        userName = findViewById(R.id.userName)
        profileImage = findViewById(R.id.profileImage)
        idVerifiedIcon = findViewById(R.id.idVerifiedIcon)
        idVerifiedText = findViewById(R.id.idVerifiedText)
        phoneVerifiedIcon = findViewById(R.id.phoneVerifiedIcon)
        phoneVerifiedText = findViewById(R.id.phoneVerifiedText)
        skillsText = findViewById(R.id.skillsText)
        experienceText = findViewById(R.id.experienceText)
        recommendButton = findViewById(R.id.recommendButton)
        engageButton = findViewById(R.id.engageButton)

        // Retrieve user data
        val user = intent.getSerializableExtra("user") as? User
        providerRef = user?.user_name
            ?: user?.id
            ?: ""
        providerName = user?.first_name + " " + user?.last_name

        serviceId = intent.getStringExtra("service_id") ?: "" // Get service ID from intent

        user?.let {
            userName.text = providerName
            experienceText.text = "Experience: ${it.details}"

            Glide.with(this)
                .load(it.pic)
                .placeholder(R.drawable.placeholder)
                .centerInside()
                .into(profileImage)

            if (it.isIdVerified == true) {
                idVerifiedIcon.visibility = ImageView.VISIBLE
                idVerifiedText.visibility = TextView.VISIBLE
            }

            if (it.isMobileVerified == true) {
                phoneVerifiedIcon.visibility = ImageView.VISIBLE
                phoneVerifiedText.visibility = TextView.VISIBLE
            }
        }

        // Back Button Click Listener
        backButton.setOnClickListener { finish() }

        // Engage Button Click Listener
        engageButton.setOnClickListener { engageWithProvider() }

        // Recommend Button (Placeholder action)
        recommendButton.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Hey, check out this app! Download it here:")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
//            Toast.makeText(this, "Recommend ${providerName} to a friend", Toast.LENGTH_SHORT).show()
        }
    }


    private fun engageWithProvider() {
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
}
