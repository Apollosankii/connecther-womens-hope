package com.womanglobal.connecther.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.womanglobal.connecther.data.EmergencyContact

object EmergencyHelper {
    private const val TAG = "EmergencyHelper"
    private const val PREFS_NAME = "emergency_contacts"
    private const val KEY_CONTACTS = "contacts"
    private const val MAX_CONTACTS = 5

    fun getContacts(context: Context): List<EmergencyContact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }

    fun saveContacts(context: Context, contacts: List<EmergencyContact>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONTACTS, Gson().toJson(contacts)).apply()
    }

    fun addContact(context: Context, contact: EmergencyContact): Boolean {
        val contacts = getContacts(context).toMutableList()
        if (contacts.size >= MAX_CONTACTS) return false
        contacts.add(contact)
        saveContacts(context, contacts)
        return true
    }

    fun removeContact(context: Context, contactId: String) {
        val contacts = getContacts(context).toMutableList()
        contacts.removeAll { it.id == contactId }
        saveContacts(context, contacts)
    }

    fun sendEmergencySms(context: Context, location: Location?) {
        val contacts = getContacts(context)
        if (contacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts to notify")
            return
        }

        val userPrefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val userName = userPrefs.getString("user_full_name", "A ConnectHer user") ?: "A ConnectHer user"

        val locationText = if (location != null) {
            "Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "Location: unavailable"
        }

        val message = "EMERGENCY ALERT: $userName needs help! " +
                "This is a GBV emergency alert from the ConnectHer app. " +
                "$locationText. Please respond immediately or call emergency services."

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS permission not granted")
            return
        }

        val smsManager = SmsManager.getDefault()
        for (contact in contacts) {
            try {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(contact.phone, null, parts, null, null)
                Log.d(TAG, "SMS sent to ${contact.name} (${contact.phone})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.name}: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location -> callback(location) }
            .addOnFailureListener {
                Log.e(TAG, "Location fetch failed: ${it.message}")
                callback(null)
            }
    }
}
