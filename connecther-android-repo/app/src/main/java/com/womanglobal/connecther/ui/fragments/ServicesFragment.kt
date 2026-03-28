package com.womanglobal.connecther.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.womanglobal.connecther.CategoryUsersActivity
import com.womanglobal.connecther.adapters.GenericGridAdapter
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.FragmentServicesBinding
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch
import java.util.Locale

class ServicesFragment : Fragment() {
    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!
    private var allServices: List<Service> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefresh.setOnRefreshListener { loadServices() }

        binding.searchServices.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterServices(s?.toString() ?: "")
            }
        })

        loadServices()
    }

    override fun onResume() {
        super.onResume()
        loadServices()
    }

    private fun loadServices() {
        lifecycleScope.launch {
            val services = runCatching { SupabaseData.getServices() }.getOrElse {
                context?.let { Toast.makeText(it, "Failed to load services", Toast.LENGTH_SHORT).show() }
                _binding?.swipeRefresh?.isRefreshing = false
                return@launch
            }
            _binding?.swipeRefresh?.isRefreshing = false
            if (!isAdded) return@launch
            allServices = services
            filterServices(binding.searchServices.text?.toString() ?: "")
        }
    }

    private fun filterServices(query: String) {
        if (!isAdded) return
        val ctx = context ?: return
        val filtered = if (query.isBlank()) {
            allServices
        } else {
            val q = query.lowercase(Locale.getDefault())
            allServices.filter {
                it.name.lowercase(Locale.getDefault()).contains(q) ||
                    it.description.lowercase(Locale.getDefault()).contains(q)
            }
        }

        val countText = "${filtered.size} service${if (filtered.size != 1) "s" else ""} available"
        binding.serviceCount.text = countText
        binding.serviceCount.visibility = View.VISIBLE

        binding.categoryRecyclerView.layoutManager = GridLayoutManager(ctx, 2)
        binding.categoryRecyclerView.adapter = GenericGridAdapter(filtered) { service ->
            if (!isAdded) return@GenericGridAdapter
            val actx = context ?: return@GenericGridAdapter
            startActivity(Intent(actx, CategoryUsersActivity::class.java).apply {
                putExtra("categoryName", service.name)
                putExtra("service_id", service.service_id)
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
