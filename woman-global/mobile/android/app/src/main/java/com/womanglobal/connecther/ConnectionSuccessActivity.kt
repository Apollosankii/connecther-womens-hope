package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

/**
 * After a booking request is sent, or (legacy) pre-booking connection step: optional chat and booking entry.
 */
class ConnectionSuccessActivity : AppCompatActivity() {

    private lateinit var providerRef: String
    private lateinit var serviceIdStr: String
    private lateinit var providerDisplayName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_success)

        providerRef = intent.getStringExtra(EXTRA_PROVIDER_REF).orEmpty()
        providerDisplayName = intent.getStringExtra(EXTRA_PROVIDER_DISPLAY_NAME).orEmpty()
        val firstName =
            providerDisplayName.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() }
                ?: providerDisplayName.trim().ifBlank { getString(R.string.booking_request_party_unknown) }
        serviceIdStr = intent.getStringExtra(EXTRA_SERVICE_ID).orEmpty()
        val picUrl = intent.getStringExtra(EXTRA_PROVIDER_PIC).orEmpty()
        val prefillPrice = intent.getStringExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE)
        val quoteJson = intent.getStringExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON)
        val postBooking = intent.getBooleanExtra(EXTRA_POST_BOOKING, false)

        if (providerRef.isBlank() || serviceIdStr.isBlank()) {
            Toast.makeText(this, R.string.service_menu_missing_params, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.connectionButtonBack).setOnClickListener { finish() }
        val headline = findViewById<TextView>(R.id.connectionHeadline)
        headline.text = if (postBooking) {
            getString(R.string.connection_success_post_booking_headline, firstName)
        } else {
            getString(R.string.connection_success_headline, firstName)
        }

        val continueBooking = findViewById<TextView>(R.id.textContinueBooking)
        if (postBooking) {
            continueBooking.visibility = View.GONE
        } else {
            continueBooking.visibility = View.VISIBLE
        }

        val serviceCaption = findViewById<TextView>(R.id.connectionServiceCaption)
        val serviceNameExtra = intent.getStringExtra(EXTRA_SERVICE_NAME).orEmpty().trim()
        if (postBooking && serviceNameExtra.isNotEmpty()) {
            serviceCaption.visibility = View.VISIBLE
            serviceCaption.text = getString(R.string.connection_success_service_line, serviceNameExtra)
        } else {
            serviceCaption.visibility = View.GONE
        }

        val avatar = findViewById<ImageView>(R.id.connectionAvatar)
        Glide.with(this)
            .load(picUrl.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.ic_avatar_neutral)
            .circleCrop()
            .into(avatar)

        findViewById<AppCompatButton>(R.id.buttonSendMessage).setOnClickListener { sendMessage() }
        continueBooking.setOnClickListener {
            openRequestBooking(prefillPrice, quoteJson)
            finish()
        }
    }

    private fun sendMessage() {
        val sid = serviceIdStr.toIntOrNull()
        if (sid == null) {
            Toast.makeText(this, R.string.profile_chat_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val btn = findViewById<AppCompatButton>(R.id.buttonSendMessage)
        btn.isEnabled = false
        lifecycleScope.launch {
            val outcome = SupabaseData.startConversationWithProvider(providerRef, sid)
            btn.isEnabled = true
            val chatCode = outcome.chatCode?.takeIf { it.isNotBlank() }
            val err = outcome.errorCode
            when {
                chatCode != null -> {
                    startActivity(
                        Intent(this@ConnectionSuccessActivity, ChatActivity::class.java).apply {
                            putExtra("chat_code", chatCode)
                            outcome.quoteId?.takeIf { it.isNotBlank() }?.let {
                                putExtra("quote_id", it)
                            }
                            putExtra("peer_display_name", providerDisplayName)
                            putExtra("providerName", providerDisplayName)
                            intent.getStringExtra(EXTRA_PROVIDER_PIC)?.takeIf { it.isNotBlank() }?.let {
                                putExtra("peer_pic", it)
                            }
                            putExtra(
                                "serviceName",
                                intent.getStringExtra(EXTRA_SERVICE_NAME).orEmpty(),
                            )
                        },
                    )
                    finish()
                }
                err == "not_authenticated" || err == "auth_required" ->
                    Toast.makeText(this@ConnectionSuccessActivity, R.string.profile_message_sign_in, Toast.LENGTH_LONG).show()
                err == "provider_not_found" ->
                    Toast.makeText(this@ConnectionSuccessActivity, R.string.profile_provider_not_found, Toast.LENGTH_SHORT).show()
                err == "cannot_chat_with_self" ->
                    Toast.makeText(this@ConnectionSuccessActivity, R.string.profile_chat_self, Toast.LENGTH_LONG).show()
                err == "network_timeout" ->
                    Toast.makeText(this@ConnectionSuccessActivity, R.string.network_request_timeout, Toast.LENGTH_LONG).show()
                else ->
                    Toast.makeText(this@ConnectionSuccessActivity, R.string.profile_chat_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openRequestBooking(prefillPrice: String?, quoteJson: String?) {
        startActivity(
            Intent(this, RequestBookingActivity::class.java).apply {
                putExtra("provider_ref", providerRef)
                putExtra("service_id", serviceIdStr)
                if (providerDisplayName.isNotBlank()) {
                    putExtra("provider_name", providerDisplayName)
                }
                prefillPrice?.takeIf { it.isNotBlank() }?.let {
                    putExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE, it)
                }
                quoteJson?.takeIf { it.isNotBlank() }?.let {
                    putExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON, it)
                }
            },
        )
    }

    companion object {
        const val EXTRA_PROVIDER_REF = "provider_ref"
        const val EXTRA_PROVIDER_DISPLAY_NAME = "provider_display_name"
        const val EXTRA_PROVIDER_PIC = "provider_pic"
        const val EXTRA_SERVICE_ID = "service_id"
        const val EXTRA_SERVICE_NAME = "service_name"
        /** When true, booking is already submitted; hide "Continue to booking" and use post-request copy. */
        const val EXTRA_POST_BOOKING = "post_booking"
    }
}
