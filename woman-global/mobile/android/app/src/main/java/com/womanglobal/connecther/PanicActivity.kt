package com.womanglobal.connecther

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.EmergencyHelper
import com.womanglobal.connecther.utils.SosShockwaveAnimator
import kotlinx.coroutines.launch

class PanicActivity : AppCompatActivity() {
    private var isGbvSelected = true
    private var outerPulse: ObjectAnimator? = null
    private var middlePulse: ObjectAnimator? = null
    /** If user grants SMS after GBV panic, send using this location. */
    private var pendingGbvSmsLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panic)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.gbvCard).setOnClickListener { setEmergencyType(true) }
        findViewById<LinearLayout>(R.id.medicalCard).setOnClickListener { setEmergencyType(false) }
        findViewById<View>(R.id.panicButton).setOnClickListener { triggerPanic() }
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
        if (isGbvSelected) {
            triggerGbvPanic()
        } else {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$NAIROBI_WOMENS_GBV_HOTLINE")))
            Toast.makeText(this, R.string.panic_medical_opening_hotline, Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerGbvPanic() {
        EmergencyHelper.getCurrentLocation(this) { location ->
            lifecycleScope.launch {
                val ok = SupabaseData.reportGbvEmergency(
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    locationText = if (location != null) "${location.latitude},${location.longitude}" else null,
                )
                if (ok) {
                    Toast.makeText(this@PanicActivity, R.string.panic_gbv_report_sent, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PanicActivity, R.string.panic_gbv_report_failed, Toast.LENGTH_LONG).show()
                }
            }

            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED -> {
                    sendGbvSmsAndNotify(location)
                }
                else -> {
                    pendingGbvSmsLocation = location
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.SEND_SMS),
                        REQUEST_SMS_GBV
                    )
                }
            }
        }
    }

    private fun sendGbvSmsAndNotify(location: Location?) {
        val contacts = EmergencyHelper.getContacts(this)
        if (contacts.isEmpty()) {
            Toast.makeText(this, R.string.panic_gbv_add_contacts_hint, Toast.LENGTH_LONG).show()
            return
        }
        EmergencyHelper.sendEmergencySms(this, location)
        Toast.makeText(this, R.string.panic_gbv_contacts_notified, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_SMS_GBV) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendGbvSmsAndNotify(pendingGbvSmsLocation)
        } else {
            Toast.makeText(this, R.string.panic_gbv_sms_permission_denied, Toast.LENGTH_LONG).show()
        }
        pendingGbvSmsLocation = null
    }

    private fun startPulseAnimation() {
        val outer = findViewById<View>(R.id.panicRingOuter)
        val middle = findViewById<View>(R.id.panicRingMiddle)
        val origin = findViewById<View>(R.id.panicButton)
        outerPulse?.cancel()
        middlePulse?.cancel()
        val pair = SosShockwaveAnimator.start(outer, middle, origin)
        outerPulse = pair.first
        middlePulse = pair.second
    }

    override fun onDestroy() {
        outerPulse?.cancel()
        middlePulse?.cancel()
        runCatching {
            SosShockwaveAnimator.resetRings(
                findViewById(R.id.panicRingOuter),
                findViewById(R.id.panicRingMiddle)
            )
        }
        super.onDestroy()
    }

    companion object {
        /** Nairobi Women’s GBV hotline — also listed under Emergency contacts. */
        const val NAIROBI_WOMENS_GBV_HOTLINE = "0800720565"
        private const val REQUEST_SMS_GBV = 7001
    }
}
