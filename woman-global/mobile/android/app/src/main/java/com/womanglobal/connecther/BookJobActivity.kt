package com.womanglobal.connecther


import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.womanglobal.connecther.R

class BookJobActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_job)

        // Get the selected date from the intent
        val selectedDate = intent.getLongExtra("selectedDate", -1L)
        val serviceId = intent.getStringExtra("service_id") ?: ""

        if (selectedDate <= 0L) {
            Toast.makeText(this, "Invalid booking date", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Calendar selection doesn't include a specific provider, so we route into:
        // - service selection (AllServicesActivity) if service_id is missing
        // - task menu / auto-match when service_id is present
        if (serviceId.isBlank()) {
            startActivity(Intent(this, AllServicesActivity::class.java))
        } else {
            startActivity(
                Intent(this, ServiceMenuActivity::class.java).apply {
                    putExtra("service_id", serviceId)
                    putExtra("service_name", getString(R.string.home_book_provider_title))
                },
            )
        }
        finish()
    }
}
