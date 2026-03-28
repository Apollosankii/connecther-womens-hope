package com.womanglobal.connecther

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.womanglobal.connecther.services.GbvEmergencyRequest
import com.womanglobal.connecther.utils.ApiServiceFactory
import com.womanglobal.connecther.utils.EmergencyHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PanicActivity : AppCompatActivity() {
    private var isGbvSelected = true
    private var outerPulse: ObjectAnimator? = null
    private var middlePulse: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panic)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.gbvCard).setOnClickListener { setEmergencyType(true) }
        findViewById<LinearLayout>(R.id.medicalCard).setOnClickListener { setEmergencyType(false) }
        findViewById<LinearLayout>(R.id.panicButton).setOnClickListener { triggerPanic() }
        setEmergencyType(true)
        startPulseAnimation()
    }

    private fun setEmergencyType(gbv: Boolean) {
        isGbvSelected = gbv
        findViewById<LinearLayout>(R.id.gbvCard).setBackgroundResource(
            if (gbv) R.drawable.bg_card_selected_primary else R.drawable.bg_card_selectable
        )
        findViewById<LinearLayout>(R.id.medicalCard).setBackgroundResource(
            if (gbv) R.drawable.bg_card_selectable else R.drawable.bg_card_selected_primary
        )
        findViewById<ImageView>(R.id.gbvCheckmark).visibility = if (gbv) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<ImageView>(R.id.medicalCheckmark).visibility = if (gbv) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<TextView>(R.id.gbvTitle).setTextColor(ContextCompat.getColor(this, if (gbv) R.color.primary else R.color.on_surface_variant))
        findViewById<TextView>(R.id.medicalTitle).setTextColor(ContextCompat.getColor(this, if (gbv) R.color.on_surface_variant else R.color.primary))
    }

    private fun triggerPanic() {
        sendEmergencySignal()
        if (isGbvSelected) {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        } else {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }
        Toast.makeText(this, "Emergency alert triggered", Toast.LENGTH_LONG).show()
    }

    private fun startPulseAnimation() {
        val outer = findViewById<android.view.View>(R.id.panicRingOuter)
        val middle = findViewById<android.view.View>(R.id.panicRingMiddle)

        outerPulse = ObjectAnimator.ofFloat(outer, "alpha", 0.35f, 0.85f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        middlePulse = ObjectAnimator.ofFloat(middle, "alpha", 0.45f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    override fun onDestroy() {
        outerPulse?.cancel()
        middlePulse?.cancel()
        super.onDestroy()
    }

    private fun sendEmergencySignal() {
        val api = ApiServiceFactory.createApiService()
        EmergencyHelper.getCurrentLocation(this) { location ->
            val req = GbvEmergencyRequest(
                latitude = location?.latitude,
                longitude = location?.longitude,
                locationText = if (location != null) "${location.latitude},${location.longitude}" else null,
                description = if (isGbvSelected) "GBV emergency" else "Medical emergency"
            )
            api.reportGbvEmergency(req).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) = Unit
                override fun onFailure(call: Call<String>, t: Throwable) = Unit
            })
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                EmergencyHelper.sendEmergencySms(this, location)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 7001)
            }
        }
    }
}

