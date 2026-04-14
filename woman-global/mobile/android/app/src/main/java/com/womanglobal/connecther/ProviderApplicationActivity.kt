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
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProviderApplicationActivity : AppCompatActivity() {
    private val selectedServices = linkedSetOf<Int>()
    private val idDocs = mutableListOf<Uri>()
    private val certDocs = mutableListOf<Uri>()

    private lateinit var serverDocsContainer: LinearLayout

    private val pickIdDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            idDocs.clear()
            idDocs.addAll(uris)
            updateIdDocSummary()
        }
    }

    private val pickCertDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            certDocs.clear()
            certDocs.addAll(uris)
            updateCertDocSummary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_application)

        serverDocsContainer = findViewById(R.id.serverDocsContainer)

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

    override fun onResume() {
        super.onResume()
        refreshUploadedDocumentsSection()
    }

    private fun updateIdDocSummary() {
        findViewById<TextView>(R.id.textIdDocSummary).text =
            formatLocalDocSummary(idDocs, "No ID document selected yet.")
    }

    private fun updateCertDocSummary() {
        findViewById<TextView>(R.id.textCertDocSummary).text =
            formatLocalDocSummary(certDocs, "No certification files selected yet.")
    }

    private fun formatLocalDocSummary(uris: List<Uri>, emptyMessage: String): String {
        if (uris.isEmpty()) return emptyMessage
        val lines = uris.map { uri ->
            uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment
                ?: "file"
        }
        return buildString {
            append("${uris.size} file(s) selected:\n")
            lines.forEach { append("• ").append(it).append('\n') }
        }.trimEnd()
    }

    private fun refreshUploadedDocumentsSection() {
        serverDocsContainer.removeAllViews()
        lifecycleScope.launch {
            val sessionOk = withContext(Dispatchers.IO) { SupabaseClientProvider.ensureSupabaseSession() }
            if (!sessionOk) {
                val tv = TextView(this@ProviderApplicationActivity).apply {
                    text = "Sign in to see documents already stored for your account."
                    textSize = 13f
                    setTextColor(getColor(R.color.on_surface_variant))
                }
                serverDocsContainer.addView(tv)
                return@launch
            }
            val docs = withContext(Dispatchers.IO) { SupabaseData.listMyVerificationDocuments() }
            if (docs.isEmpty()) {
                val tv = TextView(this@ProviderApplicationActivity).apply {
                    text =
                        "No verification files on the server yet. They appear here after a successful upload (when you submit this form or if you have submitted before)."
                    textSize = 13f
                    setTextColor(getColor(R.color.on_surface_variant))
                }
                serverDocsContainer.addView(tv)
                return@launch
            }
            docs.forEach { doc ->
                val row = LinearLayout(this@ProviderApplicationActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }
                val label = TextView(this@ProviderApplicationActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = buildString {
                        append(doc.docTypeName)
                        append(": ")
                        append(doc.fileLabel)
                        append(if (doc.verified) " (verified)" else " (pending review)")
                    }
                    textSize = 13f
                    setTextColor(getColor(R.color.on_background))
                }
                val openBtn = MaterialButton(this@ProviderApplicationActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Open"
                    textSize = 12f
                    isAllCaps = false
                    minimumWidth = 0
                    minWidth = 0
                    setOnClickListener {
                        val u = doc.signedUrl
                        if (u.isNullOrBlank()) {
                            Toast.makeText(
                                this@ProviderApplicationActivity,
                                "Could not create a temporary view link. Check Storage policies and try again.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            // In-app viewer (PDF via embedded viewer, images in WebView). ACTION_VIEW often fails for PDFs / signed URLs.
                            startActivity(
                                Intent(this@ProviderApplicationActivity, SecureProviderDocumentActivity::class.java).apply {
                                    putExtra(SecureProviderDocumentActivity.EXTRA_URL, u)
                                    putExtra(
                                        SecureProviderDocumentActivity.EXTRA_TITLE,
                                        doc.docTypeName.ifBlank { getString(R.string.secure_document_title) },
                                    )
                                },
                            )
                        }
                    }
                }
                row.addView(label)
                row.addView(openBtn)
                serverDocsContainer.addView(row)
            }
        }
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
                getSharedPreferences("user_session", MODE_PRIVATE).edit()
                    .putBoolean("isProviderPending", true)
                    .apply()
                Toast.makeText(this@ProviderApplicationActivity, "Application submitted — you'll be notified once approved.", Toast.LENGTH_LONG).show()
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
