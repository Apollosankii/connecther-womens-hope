package com.womanglobal.connecther

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.womanglobal.connecther.data.EmergencyContact
import com.womanglobal.connecther.databinding.ActivityEmergencyContactsBinding
import com.womanglobal.connecther.utils.EmergencyHelper
import java.util.UUID

class EmergencyContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEmergencyContactsBinding

    data class Helpline(
        val name: String,
        val description: String,
        val number: String,
        val iconRes: Int,
        val iconTint: Int,
        val bgRes: Int
    )

    private val helplines = listOf(
        Helpline("National Emergency", "Police, Ambulance, Fire", "112", R.drawable.ic_phone_call, R.color.primary, R.drawable.bg_icon_circle_pink),
        Helpline("GBV Command Centre", "24/7 Gender-Based Violence Helpline", "0800 428 428", R.drawable.ic_emergency_gbv, R.color.primary, R.drawable.bg_icon_circle_pink),
        Helpline(
            "Nairobi Women's GBV Hotline",
            "Nairobi County — gender-based violence support",
            "0800 720 565",
            R.drawable.ic_emergency_gbv,
            R.color.primary,
            R.drawable.bg_icon_circle_pink
        ),
        Helpline("Childline", "Child abuse & protection", "116", R.drawable.ic_heart_outline, R.color.accent_color, R.drawable.bg_icon_circle_orange),
        Helpline("POWA", "Crisis counselling for women", "011 642 4345", R.drawable.ic_emergency_gbv, R.color.primary, R.drawable.bg_icon_circle_pink),
        Helpline("Lifeline", "24/7 Crisis support", "0861 322 322", R.drawable.ic_phone_call, R.color.primary, R.drawable.bg_icon_circle_blue)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.addContactButton.setOnClickListener { showAddContactDialog() }

        loadHelplines()
        loadPersonalContacts()
    }

    override fun onResume() {
        super.onResume()
        loadPersonalContacts()
    }

    private fun loadHelplines() {
        for (helpline in helplines) {
            val view = LayoutInflater.from(this).inflate(R.layout.item_helpline, binding.contactsContainer, false)

            view.findViewById<TextView>(R.id.helplineName).text = helpline.name
            view.findViewById<TextView>(R.id.helplineDescription).text = helpline.description
            view.findViewById<TextView>(R.id.helplineNumber).text = helpline.number

            val icon = view.findViewById<ImageView>(R.id.helplineIcon)
            icon.setImageResource(helpline.iconRes)
            icon.setColorFilter(ContextCompat.getColor(this, helpline.iconTint))

            view.findViewById<View>(R.id.helplineIconBg).setBackgroundResource(helpline.bgRes)

            view.setOnClickListener {
                val dialNumber = helpline.number.replace(" ", "")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))
                startActivity(intent)
            }

            binding.contactsContainer.addView(view, binding.contactsContainer.childCount - 2)
        }
    }

    private fun loadPersonalContacts() {
        val contacts = EmergencyHelper.getContacts(this)
        binding.personalContactsContainer.removeAllViews()

        if (contacts.isNotEmpty()) {
            binding.yourContactsHeader.visibility = View.VISIBLE
            for (contact in contacts) {
                addPersonalContactView(contact)
            }
        } else {
            binding.yourContactsHeader.visibility = View.GONE
        }
    }

    private fun addPersonalContactView(contact: EmergencyContact) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_emergency_contact, binding.personalContactsContainer, false)

        val initial = contact.name.firstOrNull()?.uppercase() ?: "?"
        view.findViewById<TextView>(R.id.contactInitial).text = initial
        view.findViewById<TextView>(R.id.contactName).text = contact.name
        view.findViewById<TextView>(R.id.contactPhone).text = contact.phone

        view.findViewById<ImageView>(R.id.deleteContact).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove Contact")
                .setMessage("Remove ${contact.name} from emergency contacts?")
                .setPositiveButton("Remove") { _, _ ->
                    EmergencyHelper.removeContact(this, contact.id)
                    loadPersonalContacts()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.personalContactsContainer.addView(view)
    }

    private fun showAddContactDialog() {
        val contacts = EmergencyHelper.getContacts(this)
        if (contacts.size >= 5) {
            Toast.makeText(this, "Maximum 5 emergency contacts allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.contactNameInput)
        val phoneInput = dialogView.findViewById<EditText>(R.id.contactPhoneInput)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)

        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.saveButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()

            if (name.isEmpty()) {
                nameInput.error = "Name is required"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                phoneInput.error = "Phone number is required"
                return@setOnClickListener
            }

            val contact = EmergencyContact(
                id = UUID.randomUUID().toString(),
                name = name,
                phone = phone
            )

            EmergencyHelper.addContact(this, contact)
            dialog.dismiss()
            loadPersonalContacts()
            Toast.makeText(this, "${contact.name} added", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
