package com.womanglobal.connecther

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.EmergencyHelper
import kotlinx.coroutines.launch

class RequestBookingActivity : AppCompatActivity() {
    private var lat: Double? = null
    private var lon: Double? = null
    private var isSubmitting: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_booking)

        findViewById<MaterialButton>(R.id.buttonUseMyLocation).setOnClickListener { useGpsLocation() }
        findViewById<MaterialButton>(R.id.buttonOpenGoogleMaps).setOnClickListener {
            val uri = if (lat != null && lon != null) {
                Uri.parse("https://maps.google.com/?q=$lat,$lon")
            } else {
                Uri.parse("https://maps.google.com")
            }
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        findViewById<MaterialButton>(R.id.buttonSubmitBooking).setOnClickListener { submitBooking() }
    }

    private fun useGpsLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 5001)
            return
        }
        EmergencyHelper.getCurrentLocation(this) { location ->
            runOnUiThread {
                lat = location?.latitude
                lon = location?.longitude
                Toast.makeText(this, if (location != null) "Location captured" else "Could not get GPS location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitBooking() {
        if (isSubmitting) return
        val providerRef = (
            intent.getStringExtra("provider_ref")
                ?: intent.getStringExtra("provider_id")
                ?: intent.getStringExtra("providerId")
            ).orEmpty()
        val serviceId = (
            intent.getStringExtra("service_id")
                ?: intent.getStringExtra("serviceId")
            ).orEmpty().toIntOrNull()
        val price = findViewById<EditText>(R.id.inputPrice).text.toString().trim().toDoubleOrNull()
        val locationText = findViewById<EditText>(R.id.inputLocationNotes).text.toString().trim()
        val message = findViewById<EditText>(R.id.inputMessage).text.toString().trim()
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val submitButton = findViewById<MaterialButton>(R.id.buttonSubmitBooking)

        if (providerRef.isBlank() || serviceId == null) {
            Toast.makeText(this, "Provider/service missing", Toast.LENGTH_SHORT).show()
            return
        }
        if (price == null || price <= 0.0) {
            Toast.makeText(this, "Enter a valid price", Toast.LENGTH_SHORT).show()
            return
        }

        isSubmitting = true
        progress.visibility = View.VISIBLE
        submitButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val outcome = SupabaseData.createBookingRequest(
                    providerRef = providerRef,
                    serviceId = serviceId,
                    proposedPrice = price,
                    locationText = locationText.ifBlank { null },
                    latitude = lat,
                    longitude = lon,
                    message = message.ifBlank { null },
                )
                if (outcome.isSuccess) {
                    Toast.makeText(this@RequestBookingActivity, "Booking request sent", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    showBookingFailure(outcome.errorCode)
                }
            } catch (e: Exception) {
                val err = (e.message ?: "").lowercase()
                val mapped = when {
                    err.contains("auth") || err.contains("jwt") -> "auth_required"
                    err.contains("provider_not_found_or_unavailable") -> "provider_not_found_or_unavailable"
                    err.contains("provider_not_found") -> "provider_not_found"
                    err.contains("provider_busy") -> "provider_busy"
                    err.contains("free_tier_exhausted") -> "free_tier_exhausted"
                    err.contains("connects_exhausted") -> "connects_exhausted"
                    err.contains("insufficient_connects") -> "insufficient_connects"
                    err.contains("subscription") -> "subscription_required"
                    err.contains("duplicate_booking_same_provider") -> "duplicate_booking_same_provider"
                    err.contains("cannot_book_self") -> "cannot_book_self"
                    else -> "request_failed"
                }
                showBookingFailure(mapped)
            } finally {
                isSubmitting = false
                progress.visibility = View.GONE
                submitButton.isEnabled = true
            }
        }
    }

    private fun showBookingFailure(code: String?) {
        val msg = SupabaseData.bookingRequestErrorMessage(this, code)
        if (SupabaseData.isBookingConnectLimitError(code)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.booking_error_connects_title)
                .setMessage(msg)
                .setPositiveButton(R.string.booking_action_view_subscriptions) { _, _ ->
                    startActivity(Intent(this, SubscriptionsActivity::class.java))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
}

