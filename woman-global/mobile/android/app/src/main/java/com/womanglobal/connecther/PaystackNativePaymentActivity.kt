package com.womanglobal.connecther

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.BuildConfig
import com.paystack.android.core.Paystack
import com.paystack.android.ui.paymentsheet.PaymentSheet
import com.paystack.android.ui.paymentsheet.PaymentSheetResult
import com.womanglobal.connecther.payment.PaystackPaymentViewModel
import com.womanglobal.connecther.utils.UserFriendlyMessages

/**
 * Opens Paystack [PaymentSheet] using the user’s registered email (no manual entry).
 * Expects `plan_id` from the Subscriptions flow; email from intent and/or [user_session] prefs / Firebase.
 */
class PaystackNativePaymentActivity : AppCompatActivity() {

    private val vm: PaystackPaymentViewModel by viewModels()
    private lateinit var paymentSheet: PaymentSheet

    private lateinit var textBillingEmail: TextView
    private lateinit var buttonRetry: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    private var planId: Int = -1
    private lateinit var billingEmail: String
    private var lastAuthorizationUrl: String? = null
    private var lastReference: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paystack_native_payment)

        val pk = BuildConfig.PAYSTACK_PUBLIC_KEY.trim()
        if (pk.isBlank()) {
            Toast.makeText(
                this,
                getString(R.string.paystack_public_key_missing, getString(R.string.support_email)),
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }
        Paystack.builder()
            .setPublicKey(pk)
            .setLoggingEnabled(BuildConfig.DEBUG)
            .build()

        if (BuildConfig.DEBUG) {
            val mode = when {
                pk.startsWith("pk_live_") -> "LIVE"
                pk.startsWith("pk_test_") -> "TEST"
                pk.isEmpty() -> "MISSING"
                else -> "UNKNOWN"
            }
            Log.d(
                TAG,
                "Paystack public key mode=$mode — must match paystack-express PAYSTACK_SECRET_KEY (sk_live_* with pk_live_*, sk_test_* with pk_test_*).",
            )
        }

        textBillingEmail = findViewById(R.id.textBillingEmail)
        buttonRetry = findViewById(R.id.buttonRetry)
        progress = findViewById(R.id.progressBar)
        status = findViewById(R.id.textStatus)

        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)

        planId = intent.getIntExtra("plan_id", -1)
        billingEmail = resolveBillingEmail()

        val priceHint = intent.getStringExtra("price").orEmpty()
        findViewById<TextView>(R.id.textPlanSummary).text =
            if (planId > 0) {
                getString(R.string.paystack_plan_summary, planId, priceHint.ifBlank { "—" })
            } else {
                getString(R.string.paystack_plan_missing)
            }

        if (billingEmail.isNotBlank()) {
            textBillingEmail.visibility = View.VISIBLE
            textBillingEmail.text = getString(R.string.paystack_billing_email, billingEmail)
        } else {
            textBillingEmail.visibility = View.GONE
        }

        vm.state.observe(this) { s ->
            progress.visibility = if (s.loading) View.VISIBLE else View.GONE
            buttonRetry.visibility = if (!s.loading && s.error != null) View.VISIBLE else View.GONE
            status.text = listOfNotNull(s.error, s.message).firstOrNull().orEmpty()
        }

        vm.sheetLaunch.observe(this) { launch ->
            if (launch == null) return@observe
            lastAuthorizationUrl = launch.authorizationUrl
            lastReference = launch.reference
            if (launch.accessCode.isNotBlank()) {
                Log.d(TAG, "Launching PaymentSheet with accessCode=${launch.accessCode}, ref=${launch.reference}")
                paymentSheet.launch(launch.accessCode)
            } else if (launch.authorizationUrl.isNotBlank()) {
                Log.d(TAG, "No access_code — opening authorization_url directly")
                openHostedCheckout(launch.authorizationUrl, launch.reference)
            } else {
                vm.onSheetFlowFinished(null, getString(R.string.paystack_init_no_checkout))
            }
            vm.clearSheetLaunch()
        }

        buttonRetry.setOnClickListener { startCheckout() }

        if (planId <= 0) {
            status.text = getString(R.string.paystack_plan_missing)
            buttonRetry.visibility = View.GONE
            return
        }

        if (billingEmail.isBlank()) {
            status.text = getString(R.string.paystack_email_missing)
            Toast.makeText(this, R.string.paystack_email_missing, Toast.LENGTH_LONG).show()
            buttonRetry.visibility = View.VISIBLE
            return
        }

        if (savedInstanceState == null) {
            startCheckout()
        }
    }

    private fun resolveBillingEmail(): String {
        val fromIntent = intent.getStringExtra("email")?.trim().orEmpty()
        if (fromIntent.isNotBlank()) return fromIntent
        val fromPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
            .getString("user_email", null)
            ?.trim()
            .orEmpty()
        if (fromPrefs.isNotBlank()) return fromPrefs
        return FirebaseAuth.getInstance().currentUser?.email?.trim().orEmpty()
    }

    private fun startCheckout() {
        if (planId < 1) return
        val email = resolveBillingEmail()
        if (email.isBlank()) {
            status.text = getString(R.string.paystack_email_missing)
            Toast.makeText(this, R.string.paystack_email_missing, Toast.LENGTH_LONG).show()
            return
        }
        vm.preparePaymentSheet(planId, email)
    }

    private fun onPaymentSheetResult(result: PaymentSheetResult) {
        Log.d(TAG, "PaymentSheet result: $result")
        when (result) {
            is PaymentSheetResult.Completed -> {
                val ref = result.paymentCompletionDetails.reference.orEmpty()
                if (ref.isNotBlank() && planId > 0) {
                    vm.pollSubscriptionAfterSuccess(planId, ref) { _ ->
                        Toast.makeText(this, getString(R.string.paystack_payment_submitted, ref), Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    vm.onSheetFlowFinished(getString(R.string.paystack_payment_submitted_short), null)
                    Toast.makeText(this, getString(R.string.paystack_payment_submitted_short), Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            PaymentSheetResult.Cancelled -> {
                vm.onSheetFlowFinished(null, getString(R.string.paystack_cancelled))
            }
            is PaymentSheetResult.Failed -> {
                val raw = result.error.message.orEmpty()
                Log.w(TAG, "PaymentSheet failed: $raw", result.error)
                val friendly = UserFriendlyMessages.paystackSheetUserMessage(this, raw)
                val fallbackUrl = lastAuthorizationUrl
                if (!fallbackUrl.isNullOrBlank()) {
                    offerHostedCheckoutFallback(friendly, fallbackUrl, lastReference.orEmpty())
                } else {
                    vm.onSheetFlowFinished(null, friendly)
                }
            }
        }
    }

    private fun offerHostedCheckoutFallback(errorMsg: String, url: String, reference: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.paystack_fallback_title)
            .setMessage(getString(R.string.paystack_fallback_message) + "\n\n" + errorMsg)
            .setPositiveButton(R.string.paystack_fallback_open_browser) { _, _ ->
                openHostedCheckout(url, reference)
            }
            .setNegativeButton(R.string.paystack_fallback_cancel) { _, _ ->
                vm.onSheetFlowFinished(null, getString(R.string.paystack_cancelled))
            }
            .setCancelable(false)
            .show()
    }

    private fun openHostedCheckout(url: String, reference: String) {
        Log.d(TAG, "Opening hosted checkout: $url (ref=$reference)")
        vm.onSheetFlowFinished(getString(R.string.paystack_opening_browser), null)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            if (reference.isNotBlank() && planId > 0) {
                vm.pollSubscriptionAfterSuccess(planId, reference) { activated ->
                    if (activated) {
                        Toast.makeText(this, R.string.paystack_subscription_activated, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open browser", e)
            vm.onSheetFlowFinished(null, getString(R.string.paystack_browser_failed))
        }
    }

    companion object {
        private const val TAG = "PaystackNativePay"
    }
}
