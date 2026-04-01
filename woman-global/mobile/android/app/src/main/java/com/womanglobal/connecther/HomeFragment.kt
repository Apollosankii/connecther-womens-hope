package com.womanglobal.connecther

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
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
import com.womanglobal.connecther.utils.SosShockwaveAnimator
import com.womanglobal.connecther.utils.ThemeHelper
import com.womanglobal.connecther.utils.UIHelper
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val gson = Gson()
    private val sharedPrefKey = "cached_services"
    private var homeOuterPulse: ObjectAnimator? = null
    private var homeMiddlePulse: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Show up to 3 services immediately (cache or built-in catalog); providers see the same showcase row.
        val initialPool = getCachedServices().ifEmpty { SupabaseData.localFallbackServices() }
        setupRecyclerView(buildCategories(showcaseFromPool(initialPool)))

        loadServicesData()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHomeHeaderActions()
        binding.homeSosButton.setOnClickListener {
            startActivity(Intent(requireContext(), PanicActivity::class.java))
        }
        binding.homeEmergencyRow.setOnClickListener {
            startActivity(Intent(requireContext(), EmergencyContactsActivity::class.java))
        }
        binding.homeSeeAllServices.setOnClickListener {
            startActivity(Intent(requireContext(), AllServicesActivity::class.java))
        }
    }

    private fun setupHomeHeaderActions() {
        fun updateThemeIcon() {
            val dark = ThemeHelper.isDarkMode(requireContext())
            binding.homeThemeToggle.setImageResource(
                if (dark) R.drawable.ic_sun_24 else R.drawable.ic_moon_24
            )
        }
        updateThemeIcon()
        binding.homeThemeToggle.setOnClickListener {
            ThemeHelper.setDarkMode(requireContext(), !ThemeHelper.isDarkMode(requireContext()))
            updateThemeIcon()
            (activity as? AppCompatActivity)?.recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        startHomeSosPulse()
    }

    override fun onStop() {
        homeOuterPulse?.cancel()
        homeMiddlePulse?.cancel()
        homeOuterPulse = null
        homeMiddlePulse = null
        if (_binding != null) {
            SosShockwaveAnimator.resetRings(binding.homeSosRingOuter, binding.homeSosRingMiddle)
        }
        super.onStop()
    }

    private fun startHomeSosPulse() {
        val outer = binding.homeSosRingOuter
        val middle = binding.homeSosRingMiddle
        homeOuterPulse?.cancel()
        homeMiddlePulse?.cancel()
        val pair = SosShockwaveAnimator.start(outer, middle, binding.homeSosButton)
        homeOuterPulse = pair.first
        homeMiddlePulse = pair.second
    }

    private fun loadServicesData() {
        if (!isAdded) return
        val cacheContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val fromNetwork = runCatching { SupabaseData.getServices() }.getOrElse { emptyList() }
            if (fromNetwork.isNotEmpty()) {
                saveServicesToCache(fromNetwork, cacheContext)
            } else if (getCachedServices().isEmpty()) {
                context?.let { UIHelper.showToastShort(it, "Failed to load services") }
            }
            if (_binding == null || !isAdded) return@launch
            val pool = fromNetwork.ifEmpty { getCachedServices() }.ifEmpty { SupabaseData.localFallbackServices() }
            setupRecyclerView(buildCategories(showcaseFromPool(pool)))
        }
    }

    /** Home hero grid: showcase only (full catalog via "See all" / AllServicesActivity). */
    private fun showcaseFromPool(services: List<Service>): List<Service> = services.take(HOME_SHOWCASE_SERVICE_CAP)

    private fun buildCategories(showcaseServices: List<Service>): List<Category> {
        val serviceCategory = Category("", showcaseServices)
        val organizationItems = listOf(
            Service("0", "About Us", "about_us_image.png", fallbackImageResId = R.mipmap.about_us),
        )
        val organizationCategory = Category("Our Organization", organizationItems)
        return listOf(serviceCategory, organizationCategory)
    }

    private fun setupRecyclerView(categories: List<Category>) {
        val bind = _binding ?: return
        val ctx = context ?: return
        bind.categoryRecyclerView.layoutManager = LinearLayoutManager(ctx)
        bind.categoryRecyclerView.adapter = MultiTypeAdapter(categories, ctx)
    }

    companion object {
        private const val HOME_SHOWCASE_SERVICE_CAP = 3
    }

    override fun onDestroyView() {
        homeOuterPulse?.cancel()
        homeMiddlePulse?.cancel()
        if (_binding != null) {
            SosShockwaveAnimator.resetRings(binding.homeSosRingOuter, binding.homeSosRingMiddle)
        }
        super.onDestroyView()
        _binding = null
    }

    private fun saveServicesToCache(services: List<Service>, appContext: Context? = null) {
        val ctx = appContext ?: context?.applicationContext ?: return
        val sharedPreferences = ctx.getSharedPreferences("app_cache", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(sharedPrefKey, gson.toJson(services)).apply()
    }

    private fun getCachedServices(): List<Service> {
        val ctx = context?.applicationContext ?: return emptyList()
        val sharedPreferences = ctx.getSharedPreferences("app_cache", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(sharedPrefKey, null)
        return if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<Service>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
}
