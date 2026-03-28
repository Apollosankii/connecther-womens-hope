package com.womanglobal.connecther

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.ActivityProviderApplicationBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.ProviderSignUpRequest
import com.womanglobal.connecther.services.ProviderSignUpResponse
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.ServiceBuilder
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProviderApplicationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderApplicationBinding
    private val apiService: ApiService by lazy { ServiceBuilder.buildService(ApiService::class.java) }
    private var services: List<Service> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderApplicationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadServices()
        binding.buttonSubmit.setOnClickListener { submitApplication() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonSubmit.isEnabled = !loading
    }

    private fun loadServices() {
        setLoading(true)
        lifecycleScope.launch {
            services = SupabaseData.getServices()
            populateServiceCheckboxes(services)
            setLoading(false)
        }
    }

    private fun populateServiceCheckboxes(serviceList: List<Service>) {
        binding.servicesContainer.removeAllViews()
        val paddingPx = resources.getDimensionPixelSize(R.dimen.standard_padding)
        for (service in serviceList) {
            val checkBox = CheckBox(this).apply {
                text = service.name
                tag = service.service_id
                setPadding(paddingPx, 12, 0, 12)
            }
            binding.servicesContainer.addView(checkBox, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun getSelectedServiceIds(): List<String> {
        val ids = mutableListOf<String>()
        for (i in 0 until binding.servicesContainer.childCount) {
            val child = binding.servicesContainer.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                (child.tag as? String)?.let { ids.add(it) }
            }
        }
        return ids
    }

    private fun submitApplication() {
        val gender = binding.inputGender.text.toString().trim().takeIf { it.isNotEmpty() }
        val birthDate = binding.inputBirthDate.text.toString().trim().takeIf { it.isNotEmpty() }
        val country = binding.inputCountry.text.toString().trim().takeIf { it.isNotEmpty() } ?: "Kenya"
        val county = binding.inputCounty.text.toString().trim().takeIf { it.isNotEmpty() }
        val areaName = binding.inputAreaName.text.toString().trim().takeIf { it.isNotEmpty() }
        val natId = binding.inputNatId.text.toString().trim().takeIf { it.isNotEmpty() }
        val emmCont1 = binding.inputEmmCont1.text.toString().trim().takeIf { it.isNotEmpty() }
        val emmCont2 = binding.inputEmmCont2.text.toString().trim().takeIf { it.isNotEmpty() }

        val selectedServiceIds = getSelectedServiceIds()
        if (selectedServiceIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one service you want to offer.", Toast.LENGTH_LONG).show()
            return
        }
        val serviceIdsInt = selectedServiceIds.mapNotNull { it.toIntOrNull() }

        setLoading(true)

        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                val ok = SupabaseData.submitProviderApplication(
                    gender = gender,
                    birthDate = birthDate,
                    country = country,
                    county = county,
                    areaName = areaName,
                    natId = natId,
                    emmCont1 = emmCont1,
                    emmCont2 = emmCont2,
                    serviceIds = serviceIdsInt
                )
                setLoading(false)
                if (ok) {
                    Toast.makeText(this@ProviderApplicationActivity, "Application submitted. You will be notified when an admin reviews it.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@ProviderApplicationActivity, "Could not submit. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            val request = ProviderSignUpRequest(
                gender = gender,
                birthDate = birthDate,
                country = country,
                county = county,
                areaName = areaName,
                natId = natId,
                emmCont1 = emmCont1,
                emmCont2 = emmCont2
            )
            apiService.submitProviderApplication(request).enqueue(object : Callback<ProviderSignUpResponse> {
                override fun onResponse(call: Call<ProviderSignUpResponse>, response: Response<ProviderSignUpResponse>) {
                    setLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        Toast.makeText(this@ProviderApplicationActivity, "Application submitted. You will be notified when an admin reviews it.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val message = body?.info ?: response.errorBody()?.string() ?: "Submission failed"
                        Toast.makeText(this@ProviderApplicationActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
                override fun onFailure(call: Call<ProviderSignUpResponse>, t: Throwable) {
                    setLoading(false)
                    Toast.makeText(this@ProviderApplicationActivity, "Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }
}
