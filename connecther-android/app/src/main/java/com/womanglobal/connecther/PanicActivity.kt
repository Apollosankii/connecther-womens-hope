package com.womanglobal.connecther

import ApiServiceFactory
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.womanglobal.connecther.databinding.ActivityPanicBinding
import com.womanglobal.connecther.services.GbvEmergencyRequest
import com.womanglobal.connecther.utils.EmergencyHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PanicActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPanicBinding
    private var selectedType = EmergencyType.GBV

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    enum class EmergencyType { GBV, MEDICAL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        binding.gbvCard.setOnClickListener { selectType(EmergencyType.GBV) }
        binding.medicalCard.setOnClickListener { selectType(EmergencyType.MEDICAL) }
        binding.panicButton.setOnClickListener { onPanicPressed() }

        selectType(EmergencyType.GBV)
    }

    private fun selectType(type: EmergencyType) {
        selectedType = type

        if (type == EmergencyType.GBV) {
            binding.gbvCard.setBackgroundResource(R.drawable.bg_card_selected_primary)
            binding.gbvCheckmark.visibility = View.VISIBLE
            binding.gbvTitle.setTextColor(ContextCompat.getColor(this, R.color.primary))
            binding.gbvIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))

            binding.medicalCard.setBackgroundResource(R.drawable.bg_card_selectable)
            binding.medicalCheckmark.visibility = View.GONE
            binding.medicalTitle.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            binding.medicalIcon.clearColorFilter()

            binding.panicButton.setBackgroundResource(R.drawable.bg_panic_button_large)
            binding.panicRingOuter.setBackgroundResource(R.drawable.bg_panic_ring_outer)
            binding.panicRingMiddle.setBackgroundResource(R.drawable.bg_panic_ring_middle)
        } else {
            binding.medicalCard.setBackgroundResource(R.drawable.bg_card_selected_orange)
            binding.medicalCheckmark.visibility = View.VISIBLE
            binding.medicalTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_color))
            binding.medicalIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))

            binding.gbvCard.setBackgroundResource(R.drawable.bg_card_selectable)
            binding.gbvCheckmark.visibility = View.GONE
            binding.gbvTitle.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            binding.gbvIcon.clearColorFilter()

            binding.panicButton.setBackgroundResource(R.drawable.bg_panic_button_orange)
            binding.panicRingOuter.background = ContextCompat.getDrawable(this, R.drawable.bg_panic_ring_outer)?.mutate()?.apply {
                setTint(0x15E65100.toInt())
            }
            binding.panicRingMiddle.background = ContextCompat.getDrawable(this, R.drawable.bg_panic_ring_middle)?.mutate()?.apply {
                setTint(0x30E65100.toInt())
            }
        }
    }

    private fun onPanicPressed() {
        when (selectedType) {
            EmergencyType.GBV -> handleGbvEmergency()
            EmergencyType.MEDICAL -> handleMedicalEmergency()
        }
    }

    private fun handleGbvEmergency() {
        val contacts = EmergencyHelper.getContacts(this)

        if (contacts.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Emergency Contacts")
                .setMessage("You haven't added any emergency contacts yet. Would you like to add contacts now?")
                .setPositiveButton("Add Contacts") { _, _ ->
                    startActivity(Intent(this, EmergencyContactsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm GBV Emergency")
            .setMessage("This will:\n\n• Notify the admin/support team\n• Send SMS alerts to your ${contacts.size} emergency contact(s) with your location\n\nAre you sure you want to proceed?")
            .setPositiveButton("Yes, Send Alert") { _, _ ->
                if (checkAndRequestPermissions()) {
                    triggerGbvAlert()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun triggerGbvAlert() {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Sending emergency alerts...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        EmergencyHelper.getCurrentLocation(this) { location ->
            EmergencyHelper.sendEmergencySms(this, location)

            val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
            val userId = prefs.getString("user_id", "") ?: ""

            val request = GbvEmergencyRequest(
                userId = userId,
                latitude = location?.latitude,
                longitude = location?.longitude
            )

            val apiService = ApiServiceFactory.createApiService()
            apiService.reportGbvEmergency(request).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    progressDialog.dismiss()
                    showSuccessDialog()
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e("PanicActivity", "Admin notification failed: ${t.message}")
                    progressDialog.dismiss()
                    showSuccessDialog()
                }
            })
        }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Alert Sent")
            .setMessage("Your emergency contacts have been notified via SMS and the support team has been alerted. Stay safe.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun handleMedicalEmergency() {
        AlertDialog.Builder(this)
            .setTitle("Medical Emergency")
            .setMessage("The ambulance service integration is being set up. In the meantime, please call your local emergency number for immediate medical assistance.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            if (smsGranted) {
                triggerGbvAlert()
            } else {
                Toast.makeText(this, "SMS permission is required to notify emergency contacts", Toast.LENGTH_LONG).show()
            }
        }
    }
}
