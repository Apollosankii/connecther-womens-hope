package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.womanglobal.connecther.adapters.GenericGridAdapter
import com.womanglobal.connecther.databinding.ActivityAllServicesBinding
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class AllServicesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAllServicesBinding
    private var allServices = emptyList<com.womanglobal.connecther.data.Service>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadAllServices()
    }

    private fun loadAllServices() {
        lifecycleScope.launch {
            val services = runCatching { SupabaseData.getServices() }.getOrElse {
                Toast.makeText(this@AllServicesActivity, "Failed to load services", Toast.LENGTH_SHORT).show()
                return@launch
            }
            allServices = services
            binding.servicesRecyclerView.layoutManager = GridLayoutManager(this@AllServicesActivity, 2)
            binding.searchServices.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    renderServices(s?.toString().orEmpty())
                }
            })
            renderServices("")
        }
    }

    private fun renderServices(query: String) {
        val filtered = if (query.isBlank()) {
            allServices
        } else {
            val q = query.lowercase()
            allServices.filter { it.name.lowercase().contains(q) || (it.description?.lowercase()?.contains(q) == true) }
        }
        binding.serviceCount.text = "${filtered.size} service${if (filtered.size == 1) "" else "s"}"
        binding.servicesRecyclerView.adapter = GenericGridAdapter(filtered) { service ->
                val intent = Intent(this@AllServicesActivity, CategoryUsersActivity::class.java).apply {
                    putExtra("categoryName", service.name)
                    putExtra("service_id", service.service_id)
                }
                startActivity(intent)
            }
    }

}
