package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PaymentOptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_options)

        findViewById<Button>(R.id.btnPayButton).setOnClickListener {
            startActivity(
                Intent(this, PaystackNativePaymentActivity::class.java).apply {
                    putExtra("email", intent.getStringExtra("email"))
                    putExtra("plan_id", intent.getIntExtra("plan_id", -1))
                    putExtra("amount_kobo", intent.getIntExtra("amount_kobo", 0))
                    putExtra("price", intent.getStringExtra("price"))
                },
            )
        }
    }
}
