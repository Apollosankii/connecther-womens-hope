package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ProviderProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_profile)

        val prefs = getSharedPreferences("provider_profile", MODE_PRIVATE)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val headline = findViewById<TextInputEditText>(R.id.inputProviderHeadline)
        val experience = findViewById<TextInputEditText>(R.id.inputProviderExperience)
        val hours = findViewById<TextInputEditText>(R.id.inputProviderWorkingHours)
        val available = findViewById<SwitchMaterial>(R.id.switchAvailableForBooking)
        val docsBtn = findViewById<MaterialButton>(R.id.buttonPortfolioDocuments)
        val saveBtn = findViewById<MaterialButton>(R.id.buttonSaveProviderProfile)
        val activeJobBanner = findViewById<View>(R.id.providerActiveJobBanner)

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setNavigationIcon(R.drawable.arrow_back_24px)

        // Local fallback values (useful while Supabase auth/JWT bridging is being restored).
        headline.setText(prefs.getString("headline", ""))
        experience.setText(prefs.getString("experience", ""))
        hours.setText(prefs.getString("working_hours", ""))
        available.isChecked = prefs.getBoolean("available", true)

        // Supabase-backed load (if authenticated).
        lifecycleScope.launch {
            val profile = runCatching { SupabaseData.getMyProviderProfile() }.getOrNull()
            profile?.let {
                headline.setText(it.title.orEmpty())
                experience.setText(it.occupation.orEmpty())
                hours.setText(it.working_hours.orEmpty())
                available.isChecked = it.available_for_booking ?: true
            }
            val busy = SupabaseData.myProviderHasActiveJob()
            activeJobBanner.visibility = if (busy) View.VISIBLE else View.GONE
        }

        docsBtn.setOnClickListener {
            startActivity(Intent(this, ManageProviderDocumentsActivity::class.java))
        }

        saveBtn.setOnClickListener {
            val headlineText = headline.text?.toString()?.trim().orEmpty()
            val experienceText = experience.text?.toString()?.trim().orEmpty()
            val hoursText = hours.text?.toString()?.trim().orEmpty()
            val isAvailable = available.isChecked

            lifecycleScope.launch {
                val ok = SupabaseData.updateMyProviderProfile(
                    headline = headlineText,
                    experience = experienceText,
                    workingHours = hoursText,
                    availableForBooking = isAvailable,
                )

                if (ok) {
                    prefs.edit()
                        .putString("headline", headlineText)
                        .putString("experience", experienceText)
                        .putString("working_hours", hoursText)
                        .putBoolean("available", isAvailable)
                        .apply()
                    Toast.makeText(this@ProviderProfileActivity, "Provider profile saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProviderProfileActivity, "Failed to save profile to Supabase (auth/RLS).", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val activeJobBanner = findViewById<View>(R.id.providerActiveJobBanner)
        lifecycleScope.launch {
            val busy = SupabaseData.myProviderHasActiveJob()
            activeJobBanner.visibility = if (busy) View.VISIBLE else View.GONE
        }
    }
}

