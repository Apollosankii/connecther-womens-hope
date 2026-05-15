package com.womanglobal.connecther

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.booking.BookingQuoteIntentHelper
import com.womanglobal.connecther.booking.BookingQuoteAggregator
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.EmergencyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

class RequestBookingActivity : AppCompatActivity() {

    companion object {
        /** Decimal string, e.g. from [ServiceMenuActivity]. */
        const val EXTRA_PREFILL_PRICE = "prefill_proposed_price"
        /** JSON array of `{ "label", "unitPrice", "quantity" }` for the Quote: block. */
        const val EXTRA_QUOTE_LINES_JSON = "quote_lines_json"
        /**
         * When true, a successful submit calls [Activity.setResult] with [Activity.RESULT_OK] so a parent
         * (e.g. [ProfileActivity] in the recommendation flow) can finish and collapse the back stack.
         */
        const val EXTRA_NOTIFY_PARENT_ON_BOOKING_SUCCESS = "notify_parent_on_booking_success"
    }

    private var lat: Double? = null
    private var lon: Double? = null
    private var isSubmitting: Boolean = false
    private var bookingService: Service? = null
    private val extraFieldInputs = linkedMapOf<String, EditText>()
    private var quoteLinesJsonFromIntent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_booking)

        quoteLinesJsonFromIntent = intent.getStringExtra(EXTRA_QUOTE_LINES_JSON)
        val prefill = intent.getStringExtra(EXTRA_PREFILL_PRICE)?.toDoubleOrNull()
        if (prefill != null && prefill > 0) {
            findViewById<EditText>(R.id.inputPrice).setText(String.format(Locale.US, "%.0f", prefill))
        }

        bindOrderSubtitle()
        refreshTotalLabel()

        findViewById<ImageButton>(R.id.buttonBackRequestBooking).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.buttonUseCurrentGps).setOnClickListener { useGpsLocation() }

        findViewById<EditText>(R.id.inputPrice).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    refreshTotalLabel()
                }
            },
        )

        val serviceIdStr =
            intent.getStringExtra("service_id") ?: intent.getStringExtra("serviceId").orEmpty()
        val serviceIdInt = serviceIdStr.toIntOrNull()
        if (serviceIdInt != null) {
            lifecycleScope.launch {
                val svc = SupabaseData.getServiceById(serviceIdInt)
                withContext(Dispatchers.Main) {
                    bookingService = svc
                    applyServiceLocationFields(svc)
                }
            }
        }

        findViewById<AppCompatButton>(R.id.buttonSubmitBooking).setOnClickListener { submitBooking() }
    }

    private fun bindOrderSubtitle() {
        val serviceName = intent.getStringExtra("service_name").orEmpty().trim()
        val providerName = intent.getStringExtra("provider_name").orEmpty().trim()
        val serviceIdStr = (intent.getStringExtra("service_id") ?: intent.getStringExtra("serviceId")).orEmpty()
        val svcLine = serviceName.ifBlank { getString(R.string.booking_detail_service, serviceIdStr.ifBlank { "—" }) }
        val ref = (
            intent.getStringExtra("provider_ref")
                ?: intent.getStringExtra("provider_id")
                ?: intent.getStringExtra("providerId")
        ).orEmpty().trim()
        val who = providerName.ifBlank {
            ref.ifBlank { getString(R.string.booking_request_party_unknown) }
        }
        findViewById<TextView>(R.id.textOrderSubtitle).text =
            getString(R.string.booking_order_subtitle_format, svcLine, who)
    }

    private fun refreshTotalLabel() {
        val price = findViewById<EditText>(R.id.inputPrice).text.toString().trim().toDoubleOrNull()
        val tv = findViewById<TextView>(R.id.textTotalLabel)
        tv.text =
            if (price != null && price > 0) {
                getString(R.string.booking_total_format, price)
            } else {
                getString(R.string.booking_total_placeholder)
            }
    }

    private fun applyServiceLocationFields(svc: Service?) {
        val container = findViewById<LinearLayout>(R.id.extraLocationFieldsContainer)
        val label = findViewById<TextView>(R.id.labelExtraLocation)
        container.removeAllViews()
        extraFieldInputs.clear()
        if (svc?.require_location_detail != true) {
            label.visibility = View.GONE
            container.visibility = View.GONE
            return
        }
        label.visibility = View.VISIBLE
        container.visibility = View.VISIBLE
        val arr = runCatching { JSONArray(svc.location_detail_schema_json) }.getOrNull()
        if (arr == null || arr.length() == 0) {
            val et = EditText(this).apply {
                hint = getString(R.string.booking_extra_address_hint)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ContextCompat.getDrawable(this@RequestBookingActivity, R.drawable.rounded_edittext)
            }
            container.addView(et)
            extraFieldInputs["detail"] = et
            return
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("key").ifBlank { "field_$i" }
            val lab = o.optString("label").ifBlank { key }
            val et = EditText(this).apply {
                hint = lab
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ContextCompat.getDrawable(this@RequestBookingActivity, R.drawable.rounded_edittext)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(8)
            et.layoutParams = lp
            container.addView(et)
            extraFieldInputs[key] = et
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun collectLocationExtra(): Map<String, String> =
        extraFieldInputs.mapNotNull { (k, et) ->
            val v = et.text.toString().trim()
            if (v.isEmpty()) null else k to v
        }.toMap()

    private fun useGpsLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 5001)
            return
        }
        EmergencyHelper.getCurrentLocation(this) { location ->
            runOnUiThread {
                lat = location?.latitude
                lon = location?.longitude
                val coords = findViewById<TextView>(R.id.textGpsCoords)
                if (location != null) {
                    coords.text = String.format(
                        Locale.US,
                        "GPS: %.5f, %.5f",
                        location.latitude,
                        location.longitude,
                    )
                    coords.visibility = View.VISIBLE
                } else {
                    coords.visibility = View.GONE
                }
                Toast.makeText(
                    this,
                    if (location != null) {
                        getString(R.string.booking_location_captured)
                    } else {
                        getString(R.string.booking_location_failed)
                    },
                    Toast.LENGTH_SHORT,
                ).show()
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
        val messageRaw = findViewById<EditText>(R.id.inputMessage).text.toString().trim()
        val quoteLines = BookingQuoteIntentHelper.decodeLines(quoteLinesJsonFromIntent)
        val message =
            BookingQuoteAggregator.appendQuoteBreakdown(
                messageRaw.ifBlank { null },
                quoteLines,
            )
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val submitButton = findViewById<AppCompatButton>(R.id.buttonSubmitBooking)

        if (providerRef.isBlank() || serviceId == null) {
            Toast.makeText(this, "Provider/service missing", Toast.LENGTH_SHORT).show()
            return
        }
        if (price == null || price <= 0.0) {
            Toast.makeText(this, "Enter a valid price", Toast.LENGTH_SHORT).show()
            return
        }

        val svc = bookingService
        val extra = collectLocationExtra()
        if (svc?.require_location_detail == true && extra.isEmpty()) {
            Toast.makeText(this, R.string.booking_extra_required_toast, Toast.LENGTH_LONG).show()
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
                    message = message?.takeIf { it.isNotBlank() },
                    locationExtra = extra,
                )
                if (outcome.isSuccess) {
                    val serviceIdStr = (
                        intent.getStringExtra("service_id")
                            ?: intent.getStringExtra("serviceId")
                        ).orEmpty()
                    val displayName = intent.getStringExtra("provider_name").orEmpty().trim()
                        .ifBlank { providerRef }
                    val pic = intent.getStringExtra("provider_pic").orEmpty()
                    val serviceName = intent.getStringExtra("service_name").orEmpty()
                    startActivity(
                        Intent(this@RequestBookingActivity, ConnectionSuccessActivity::class.java).apply {
                            putExtra(ConnectionSuccessActivity.EXTRA_PROVIDER_REF, providerRef)
                            putExtra(ConnectionSuccessActivity.EXTRA_PROVIDER_DISPLAY_NAME, displayName)
                            putExtra(ConnectionSuccessActivity.EXTRA_PROVIDER_PIC, pic)
                            putExtra(ConnectionSuccessActivity.EXTRA_SERVICE_ID, serviceIdStr)
                            putExtra(ConnectionSuccessActivity.EXTRA_SERVICE_NAME, serviceName)
                            putExtra(ConnectionSuccessActivity.EXTRA_POST_BOOKING, true)
                        },
                    )
                    if (intent.getBooleanExtra(EXTRA_NOTIFY_PARENT_ON_BOOKING_SUCCESS, false)) {
                        setResult(Activity.RESULT_OK)
                    }
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
                    err.contains("location_detail_required") -> "location_detail_required"
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
