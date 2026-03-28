package com.womanglobal.connecther

import ApiServiceFactory
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.EngageRequest
import com.womanglobal.connecther.utils.UIHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

    private lateinit var providerId: String
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
        providerId = user?.user_name ?: ""
//        UIHelper.showToastLong(this, providerId)
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
        if (serviceId.isEmpty()) {
            Toast.makeText(this, "Service ID is missing!", Toast.LENGTH_SHORT).show()
            return
        }

        val engageRequest = EngageRequest(provider_id = providerId, service_id = serviceId.toInt())
        val apiService: ApiService = ApiServiceFactory.createApiService()

        apiService.engageProvider(engageRequest).enqueue(object : Callback<Any> {
            override fun onResponse(call: Call<Any>, response: Response<Any>) {
                Log.e("Patrice 1111", response.body().toString())

                if (response.body() != null) {
                    val responseBody = response.body()

                    when (responseBody) {
                        is String -> {
                            // If the API returns `false`, go to ConversationsActivity
                            if (responseBody == "exist") {
                                goToConversations()
                            }
                        }

                        is Map<*, *> -> {
                            // If the API returns a JSON object with `quote_id` and `chat_code`
                            val quoteId = responseBody["quote_id"] as? String
                            val chatCode = responseBody["chat_code"] as? String

                            if (quoteId != null && chatCode != null) {
                                openChatActivity(quoteId, chatCode)
                            } else {
                                Toast.makeText(this@ProfileActivity, "Invalid response from server", Toast.LENGTH_SHORT).show()
                            }
                        }

                        else -> {
                            Toast.makeText(this@ProfileActivity, "Unexpected response from server", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@ProfileActivity, "Engagement failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Any>, t: Throwable) {
                Log.e("Patrice", "ON Fail: $t")
                Toast.makeText(this@ProfileActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Navigates to the chat activity with the provided chat code & quote ID.
     */
    private fun openChatActivity(quoteId: String, chatCode: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_code", chatCode)
            putExtra("quote_id", quoteId)
            putExtra("providerName", providerName)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    /**
     * Navigates to the conversations activity.
     */
    private fun goToConversations() {
        val intent = Intent(this, ConversationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

}
