package com.womanglobal.connecther

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.termsContent).text = TERMS_TEXT
        findViewById<TextView>(R.id.privacyPolicyContent).text = PRIVACY_TEXT

        findViewById<TextView>(R.id.privacyPolicyOnlineLink).setOnClickListener {
            val url = getString(R.string.privacy_policy_url).trim()
            if (url.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    companion object {
        private val TERMS_TEXT = """
1. Acceptance of Terms
By downloading, installing, or using the ConnectHer application ("App") you agree to these Terms of Service. If you do not agree, do not use the App.

2. Eligibility
You must be at least 18 years old (or the age of majority in your jurisdiction) to create an account.

3. Account Responsibilities
You are responsible for maintaining the confidentiality of your login credentials and for all activity under your account. Notify us immediately if you suspect unauthorised access.

4. Acceptable Use
You agree not to:
  • Post false, misleading, or defamatory content.
  • Harass, threaten, or discriminate against other users.
  • Attempt to reverse-engineer, hack, or disrupt the App.
  • Use the App for any unlawful purpose.

5. Services & Bookings
ConnectHer connects service seekers with service providers. We do not employ or endorse any provider. All bookings are agreements between the parties; ConnectHer facilitates but is not a party to those agreements.

6. Payments
Payments processed through the App (via Paystack or other gateways) are subject to the respective gateway's terms. ConnectHer is not liable for payment disputes between users and payment processors.

7. Content Ownership
Content you post remains yours. By submitting content you grant ConnectHer a non-exclusive, royalty-free, worldwide licence to display it within the App for service delivery purposes.

8. Limitation of Liability
To the maximum extent permitted by law, ConnectHer and its affiliates shall not be liable for indirect, incidental, or consequential damages arising from use of the App.

9. Emergency Features
The panic / GBV alert feature sends SMS to your saved emergency contacts and may report your location to ConnectHer support. It is not a substitute for calling local emergency services (e.g. 999, 112, or local hotlines).

10. Termination
We may suspend or terminate your account at any time for breach of these terms.

11. Changes
We may update these terms. Continued use after changes constitutes acceptance. Material changes will be notified via the App or email.

12. Governing Law
These terms are governed by the laws of Kenya unless otherwise required by your local jurisdiction.

Contact: connecther05@gmail.com
        """.trimIndent()

        private val PRIVACY_TEXT = """
1. Information We Collect
  • Account details: name, email, phone, profile photo.
  • Location data: when you grant permission (used for provider matching, maps, and emergency alerts).
  • Device information: OS version, FCM token (for push notifications).
  • Usage data: pages viewed, features used, crash logs.

2. How We Use Your Data
  • To provide, personalise, and improve the App.
  • To match seekers with nearby service providers.
  • To process bookings, payments, and communications.
  • To send push notifications about booking status, messages, and safety alerts.
  • To comply with legal obligations.

3. Data Sharing
We share data only as necessary:
  • With the other party in a booking (name, service, location).
  • With payment processors (Paystack) for transaction processing.
  • With Firebase / Google for authentication, analytics, and push notifications.
  • With Supabase for secure data storage and real-time features.
  • With law enforcement if required by law.

4. Data Security
We use HTTPS, row-level security, and encrypted storage. No system is perfectly secure; we cannot guarantee absolute protection.

5. Data Retention
Account data is retained while your account is active. You may request deletion by contacting connecther05@gmail.com.

6. Your Rights
Depending on your jurisdiction you may have the right to access, correct, or delete your personal data. Contact connecther05@gmail.com to exercise these rights.

7. Children
The App is not intended for users under 18. We do not knowingly collect data from minors.

8. Changes to This Policy
We may update this policy. Significant changes will be communicated via the App or email.

9. Contact
For privacy inquiries: connecther05@gmail.com

WomanGlobal — ConnectHer
        """.trimIndent()
    }
}
