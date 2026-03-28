package com.womanglobal.connecther

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.womanglobal.connecther.adapters.MultiTypeAdapter
import com.womanglobal.connecther.data.Category
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.FragmentHomeBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.UIHelper
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val gson = Gson()
    private val sharedPrefKey = "cached_services"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Retrieve the value of isProvider from SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isProvider = sharedPreferences.getBoolean("isProvider", false) // Default is false


        // Load cached services before making an API call
        val cachedServices = getCachedServices()
        if (cachedServices.isNotEmpty()) {
            setupRecyclerView(
                buildCategories(cachedServices),
                isProvider = isProvider
            )
        }

        // Fetch fresh data
        loadServicesData(isProvider)

        return binding.root
    }

    private fun loadServicesData(
        isProvider: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val services = runCatching { SupabaseData.getServices() }.getOrElse {
                UIHelper.showToastShort(requireContext(), "Failed to load services")
                return@launch
            }

            saveServicesToCache(services)
            setupRecyclerView(
                buildCategories(services),
                isProvider = isProvider
            )
        }
    }

    private fun buildCategories(services: List<Service>): List<Category> {
        val serviceCategory = Category("Services", services.take(4)) // Show only first 4 services
        val organizationItems = listOf(
            Service("0", "About Us", "about_us_image.png", fallbackImageResId = R.mipmap.about_us),
            Service("0", "Donate to Us", "donate_image.png", fallbackImageResId = R.mipmap.donate)
        )
        val organizationCategory = Category("Our Organization", organizationItems)
        val gbvHotlineCategory = Category(
            "GBV Hotline", listOf(
                Service("0", "Panic Button", "gbv_hotline_image.png", fallbackImageResId = R.mipmap.gbv_hotline)
            )
        )

        return listOf(serviceCategory, organizationCategory, gbvHotlineCategory)
    }

    private fun setupRecyclerView(categories: List<Category>, isProvider : Boolean) {
        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.categoryRecyclerView.adapter = MultiTypeAdapter(categories, requireContext(), isProvider)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Save services list to SharedPreferences (cache)
    private fun saveServicesToCache(services: List<Service>) {
        val sharedPreferences = requireContext().getSharedPreferences("app_cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(sharedPrefKey, gson.toJson(services))
        editor.apply()
    }

    // Retrieve cached services from SharedPreferences
    private fun getCachedServices(): List<Service> {
        val sharedPreferences = requireContext().getSharedPreferences("app_cache", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(sharedPrefKey, null)
        return if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<Service>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
}
