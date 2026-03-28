package com.womanglobal.connecther

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ProviderApplicationActivity : AppCompatActivity() {
    private val selectedServices = linkedSetOf<Int>()
    private val idDocs = mutableListOf<Uri>()
    private val certDocs = mutableListOf<Uri>()

    private val pickIdDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            idDocs.clear()
            idDocs.addAll(uris)
            findViewById<TextView>(R.id.textIdDocSummary).text = "${idDocs.size} file(s) selected"
        }
    }

    private val pickCertDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            certDocs.clear()
            certDocs.addAll(uris)
            findViewById<TextView>(R.id.textCertDocSummary).text = "${certDocs.size} file(s) selected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_application)

        findViewById<MaterialButton>(R.id.buttonPickIdDocs).setOnClickListener {
            pickIdDocs.launch(arrayOf("*/*"))
        }
        findViewById<MaterialButton>(R.id.buttonPickCertDocs).setOnClickListener {
            pickCertDocs.launch(arrayOf("*/*"))
        }

        lifecycleScope.launch {
            val services = runCatching { SupabaseData.getServices() }.getOrDefault(emptyList())
            val container = findViewById<LinearLayout>(R.id.servicesContainer)
            services.forEach { service ->
                val check = MaterialCheckBox(this@ProviderApplicationActivity).apply {
                    text = service.name
                    setOnCheckedChangeListener { _, checked ->
                        val id = service.service_id.toIntOrNull() ?: return@setOnCheckedChangeListener
                        if (checked) selectedServices.add(id) else selectedServices.remove(id)
                    }
                }
                container.addView(check)
            }
        }

        findViewById<Button>(R.id.buttonSubmit).setOnClickListener { submitApplication() }
    }

    private fun submitApplication() {
        if (selectedServices.isEmpty()) {
            Toast.makeText(this, "Select at least one service", Toast.LENGTH_SHORT).show()
            return
        }
        if (idDocs.isEmpty() || certDocs.isEmpty()) {
            Toast.makeText(this, "Please choose required documents", Toast.LENGTH_SHORT).show()
            return
        }

        val progress = findViewById<ProgressBar>(R.id.progressBar)
        progress.visibility = View.VISIBLE

        val gender = findViewById<EditText>(R.id.inputGender).text.toString().trim()
        val birthDate = findViewById<EditText>(R.id.inputBirthDate).text.toString().trim()
        val country = findViewById<EditText>(R.id.inputCountry).text.toString().trim()
        val county = findViewById<EditText>(R.id.inputCounty).text.toString().trim()
        val area = findViewById<EditText>(R.id.inputAreaName).text.toString().trim()
        val natId = findViewById<EditText>(R.id.inputNatId).text.toString().trim()
        val em1 = findViewById<EditText>(R.id.inputEmmCont1).text.toString().trim()
        val em2 = findViewById<EditText>(R.id.inputEmmCont2).text.toString().trim()
        val headline = findViewById<EditText>(R.id.inputProfessionalTitle).text.toString().trim()
        val experience = findViewById<EditText>(R.id.inputExperience).text.toString().trim()
        val hours = findViewById<EditText>(R.id.inputWorkingHours).text.toString().trim()

        getSharedPreferences("provider_profile", MODE_PRIVATE).edit()
            .putString("headline", headline)
            .putString("experience", experience)
            .putString("working_hours", hours)
            .apply()

        lifecycleScope.launch {
            val docsUploaded = uploadVerificationDocs()
            if (!docsUploaded) {
                progress.visibility = View.GONE
                Toast.makeText(
                    this@ProviderApplicationActivity,
                    "Could not upload verification documents. Check internet and try again.",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            val ok = runCatching {
                SupabaseData.submitProviderApplication(
                    gender = gender,
                    birthDate = birthDate,
                    country = country,
                    county = county,
                    areaName = area,
                    natId = natId,
                    emmCont1 = em1,
                    emmCont2 = em2,
                    serviceIds = selectedServices.toList(),
                    workingHours = hours,
                    professionalTitle = headline,
                    experience = experience,
                )
            }.getOrDefault(false)

            progress.visibility = View.GONE
            if (ok) {
                getSharedPreferences("user_session", MODE_PRIVATE).edit().putBoolean("isProvider", true).apply()
                Toast.makeText(this@ProviderApplicationActivity, "Application submitted", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@ProviderApplicationActivity, ProviderProfileActivity::class.java))
                finish()
            } else {
                Toast.makeText(this@ProviderApplicationActivity, "Submission failed. Check Supabase auth/RLS.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun uploadVerificationDocs(): Boolean {
        val idOk = idDocs.all { uploadSingleDoc(it, "id") }
        val certOk = certDocs.all { uploadSingleDoc(it, "certificate") }
        return idOk && certOk
    }

    private suspend fun uploadSingleDoc(uri: Uri, docType: String): Boolean {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return false
        val safeName = (uri.lastPathSegment ?: "$docType-${System.currentTimeMillis()}.bin")
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return SupabaseData.uploadProviderVerificationDocument(bytes, safeName, docType)
    }
}
