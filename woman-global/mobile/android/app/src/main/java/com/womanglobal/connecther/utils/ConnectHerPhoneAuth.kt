package com.womanglobal.connecther.utils

import android.app.Activity
import android.widget.Toast

object ConnectHerPhoneAuth {

    /** E.164 for Kenya-style entry (0…, 254…, +254…, or 9 digits). */
    fun normalizeKenyaE164(raw: String): String? {
        val s = raw.trim().replace(" ", "").replace("-", "")
        if (s.isEmpty()) return null
        return when {
            s.startsWith("+254") && s.length >= 12 -> s
            s.startsWith("254") && s.length >= 11 -> "+$s"
            s.startsWith("0") && s.length >= 9 -> "+254${s.drop(1)}"
            s.length == 9 && s.all { it.isDigit() } -> "+254$s"
            s.startsWith("+") && s.length >= 10 -> s
            else -> null
        }
    }

    fun toastInvalidPhone(activity: Activity) {
        Toast.makeText(activity, com.womanglobal.connecther.R.string.auth_phone_invalid, Toast.LENGTH_LONG).show()
    }
}
