package com.womanglobal.connecther

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PaymentOptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_options)

        val payButton: Button = findViewById(R.id.btnPayButton)

        payButton.setOnClickListener {
            openSafaricomToolkit()
        }

        val price = intent.getStringExtra("price") ?: "0"
        val instructionTextView: TextView = findViewById(R.id.tillInstructionsTxt)

        setFormattedInstructions(instructionTextView, price)
    }

    private fun openSafaricomToolkit() {
        val simToolKitIntent = packageManager.getLaunchIntentForPackage("com.android.stk")
        if (simToolKitIntent != null) {
            startActivity(simToolKitIntent)
        } else {
            Toast.makeText(this, "NO SIM CARD FOUND", Toast.LENGTH_LONG).show()
        }
    }

    private fun setFormattedInstructions(textView: TextView, price: String) {
        val instructions = "1. Go to Lipa na M-Pesa\n" +
                "2. Buy Goods and Services\n" +
                "3. Enter Till Shown Above\n" +
                "4. Enter Amount: $price\n" +
                "5. Enter M-Pesa Pin"

        val spannable = SpannableString(instructions)
        val priceIndex = instructions.indexOf(price)

        if (priceIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_color)), // Unique color
                priceIndex,
                priceIndex + price.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                priceIndex,
                priceIndex + price.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = spannable
    }
}



