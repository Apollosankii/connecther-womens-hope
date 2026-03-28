package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadAllServices()
    }

    private fun loadAllServices() {
        lifecycleScope.launch {
            val services = SupabaseData.getServices()
            if (services.isNotEmpty()) {
                binding.servicesRecyclerView.layoutManager = GridLayoutManager(this@AllServicesActivity, 2)
                binding.servicesRecyclerView.adapter = GenericGridAdapter(services) { service ->
                    val intent = Intent(this@AllServicesActivity, CategoryUsersActivity::class.java).apply {
                        Log.d("AllServices", service.toString())
                        putExtra("categoryName", service.name)
                        putExtra("service_id", service.service_id)
                    }
                    startActivity(intent)
                }
            } else {
                Toast.makeText(this@AllServicesActivity, "No services available", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
