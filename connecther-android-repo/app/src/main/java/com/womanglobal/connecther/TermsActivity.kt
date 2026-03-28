package com.womanglobal.connecther

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        val termsTextView = findViewById<TextView>(R.id.termsContent)
        val privacyPolicyTextView = findViewById<TextView>(R.id.privacyPolicyContent)

        val termsContent = """
            1. **Acceptance of Terms**  
            By using this application, you agree to abide by these terms and conditions. Your continued use of the app signifies your acceptance of these terms.

            2. **User Responsibilities**  
            You are responsible for the security and confidentiality of your account information and for all activities conducted under your account.

            3. **Prohibited Activities**  
            You agree not to misuse the app. Prohibited activities include, but are not limited to, attempting to hack or disrupt the service, posting inappropriate content, or impersonating others.

            4. **Content Ownership**  
            All content you post or submit remains yours. However, by submitting content, you grant the app a non-exclusive, worldwide, royalty-free license to use, modify, and display your content.

            5. **Termination**  
            We reserve the right to terminate or restrict your account at any time if you breach these terms or engage in prohibited activities.
        """.trimIndent()

        val privacyPolicyContent = """
            **Privacy Policy**

            1. **Data Collection**  
            We collect information you provide directly to us, such as when you create or modify your account, or communicate with us. We also collect usage information about how you interact with the app.

            2. **Data Usage**  
            The information collected is used to enhance your experience with the app, to understand usage patterns, and to improve our services.

            3. **Third-Party Services**  
            We may share your data with third-party service providers for app functionality, such as payment processing, but only as necessary.

            4. **Data Security**  
            We take reasonable measures to protect your data, but no security system is impenetrable. We cannot guarantee complete security.

            5. **Changes to this Policy**  
            We may update this policy periodically. We will notify you of any significant changes.
        """.trimIndent()

        termsTextView.text = termsContent
        privacyPolicyTextView.text = privacyPolicyContent
    }
}
