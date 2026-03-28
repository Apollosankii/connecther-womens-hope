package com.womanglobal.connecther

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.womanglobal.connecther.BuildConfig
import com.paystack.android.core.Paystack
import com.paystack.android.ui.paymentsheet.PaymentSheet
import com.paystack.android.ui.paymentsheet.PaymentSheetResult
import com.womanglobal.connecther.payment.PaystackPaymentViewModel

/**
 * Native Paystack [PaymentSheet] (card, USSD, bank transfer, mobile money, etc. — per Paystack + [PAYSTACK_CHANNELS]).
 *
 * Expects:
 * - `plan_id` (Int) from Subscriptions flow
 * - `email` (String) optional default
 */
class PaystackNativePaymentActivity : AppCompatActivity() {

    private val vm: PaystackPaymentViewModel by viewModels()
    private lateinit var paymentSheet: PaymentSheet

    private lateinit var inputEmail: EditText
    private lateinit var buttonPay: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paystack_native_payment)

        val pk = BuildConfig.PAYSTACK_PUBLIC_KEY.trim()
        if (pk.isBlank()) {
            Toast.makeText(this, "Paystack public key missing. Set PAYSTACK_PUBLIC_KEY in build or .env.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Paystack.builder().setPublicKey(pk).build()

        inputEmail = findViewById(R.id.inputEmail)
        buttonPay = findViewById(R.id.buttonPayNow)
        progress = findViewById(R.id.progressBar)
        status = findViewById(R.id.textStatus)

        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)

        val planId = intent.getIntExtra("plan_id", -1)
        intent.getStringExtra("email")?.let { if (it.isNotBlank()) inputEmail.setText(it) }

        val priceHint = intent.getStringExtra("price").orEmpty()
        findViewById<TextView>(R.id.textPlanSummary).text =
            if (planId > 0) {
                getString(R.string.paystack_plan_summary, planId, priceHint.ifBlank { "—" })
            } else {
                getString(R.string.paystack_plan_missing)
            }

        vm.state.observe(this) { s ->
            progress.visibility = if (s.loading) View.VISIBLE else View.GONE
            buttonPay.isEnabled = !s.loading && planId > 0
            status.text = listOfNotNull(s.error, s.message).firstOrNull().orEmpty()
        }

        vm.sheetLaunch.observe(this) { launch ->
            if (launch == null) return@observe
            paymentSheet.launch(launch.accessCode)
            vm.clearSheetLaunch()
        }

        buttonPay.setOnClickListener {
            val email = inputEmail.text?.toString()?.trim().orEmpty()
            vm.preparePaymentSheet(planId, email)
        }

        if (planId <= 0) {
            buttonPay.isEnabled = false
            status.text = getString(R.string.paystack_plan_missing)
        }
    }

    private fun onPaymentSheetResult(result: PaymentSheetResult) {
        Log.d(TAG, "PaymentSheet result: $result")
        when (result) {
            is PaymentSheetResult.Completed -> {
                val ref = result.paymentCompletionDetails.reference.orEmpty()
                val planId = intent.getIntExtra("plan_id", -1)
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
                vm.onSheetFlowFinished(null, "Payment cancelled.")
            }
            is PaymentSheetResult.Failed -> {
                vm.onSheetFlowFinished(null, result.error.message ?: "Payment failed.")
            }
        }
    }

    companion object {
        private const val TAG = "PaystackNativePay"
    }
}
