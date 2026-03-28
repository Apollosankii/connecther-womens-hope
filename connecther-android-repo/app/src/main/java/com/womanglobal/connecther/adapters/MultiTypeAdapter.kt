package com.womanglobal.connecther.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.AboutUsActivity
import com.womanglobal.connecther.AllServicesActivity
import com.womanglobal.connecther.CategoryUsersActivity
import com.womanglobal.connecther.SearchActivity
import com.womanglobal.connecther.data.Category
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.ItemGenericBinding
import com.womanglobal.connecther.databinding.ItemTopBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.ServiceBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.net.toUri
import androidx.viewpager2.widget.ViewPager2
import com.womanglobal.connecther.R

class MultiTypeAdapter(
    private val categories: List<Category>,
    private val context: Context,
    private val isProvider: Boolean

) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TOP = 0
        private const val VIEW_TYPE_SERVICES_GRID = 1
        private const val VIEW_TYPE_LOAD_MORE = 2
        private const val VIEW_TYPE_ORGANIZATION = 3
        private const val VIEW_TYPE_GBV_HOTLINE = 4
    }

    override fun getItemViewType(position: Int): Int {
        return if (isProvider) {
            when (position) {
                0 -> VIEW_TYPE_TOP
                1 -> VIEW_TYPE_ORGANIZATION
                2 -> VIEW_TYPE_GBV_HOTLINE
                else -> throw IllegalArgumentException("Invalid position for provider: $position")
            }
        } else {
            when (position) {
                0 -> VIEW_TYPE_TOP
                1 -> VIEW_TYPE_SERVICES_GRID
                2 -> VIEW_TYPE_LOAD_MORE
                3 -> VIEW_TYPE_ORGANIZATION
                else -> throw IllegalArgumentException("Invalid position for non-provider: $position")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TOP -> {
                val binding = ItemTopBinding.inflate(inflater, parent, false)
                TopViewHolder(binding)
            }

            VIEW_TYPE_SERVICES_GRID -> {
                val binding = ItemGenericBinding.inflate(inflater, parent, false)
                ServicesGridViewHolder(binding)
            }
            VIEW_TYPE_LOAD_MORE -> {
                val binding = ItemGenericBinding.inflate(inflater, parent, false)
                LoadMoreViewHolder(binding)
            }
            VIEW_TYPE_ORGANIZATION -> {
                val binding = ItemGenericBinding.inflate(inflater, parent, false)
                OrganizationViewHolder(binding)
            }
            VIEW_TYPE_GBV_HOTLINE -> {
                val binding = ItemGenericBinding.inflate(inflater, parent, false)
                GBVHotlineViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TopViewHolder -> holder.bind()
            is ServicesGridViewHolder -> if (categories.isNotEmpty()) {
                val cat = categories[0]
                holder.bind(cat.services?.take(4) ?: emptyList(), cat.name)
            }
            is LoadMoreViewHolder -> holder.bind()
            is OrganizationViewHolder -> if (categories.size > 1) {
                val cat = categories[1]
                holder.bind(cat.services ?: emptyList(), cat.name)
            }
            is GBVHotlineViewHolder -> if (categories.size > 2) {
                val cat = categories[2]
                if (!(cat.services?.isEmpty() ?: true)) {
                    holder.bind(cat.services!![0])
                }
            }
        }
    }

     override fun getItemCount(): Int = if (isProvider) 3 else 4


    inner class TopViewHolder(private val binding: ItemTopBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            val images = listOf(R.mipmap.connecther_banner_one, R.mipmap.connecther_banner_two)
            binding.bannerViewPager.adapter = ImageSliderAdapter(images)
            binding.bannerViewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                var currentPage = 0
                override fun run() {
                    if (currentPage == images.size) {
                        currentPage = 0
                    }
                    binding.bannerViewPager.currentItem = currentPage++
                    handler.postDelayed(this, 3000) // Change image every 3 seconds
                }
            }
            handler.postDelayed(runnable, 3000)
            // Open SearchActivity when search field is clicked
            binding.searchField.setOnClickListener {
                val intent = Intent(context, SearchActivity::class.java)
                context.startActivity(intent)
            }

            // Voice search button opens voice search in SearchActivity
            binding.audioSearchButton.setOnClickListener {
                val intent = Intent(context, SearchActivity::class.java).apply {
                    putExtra("voiceSearch", true) // Flag to trigger voice search
                }
                context.startActivity(intent)
            }
        }
    }



    inner class ServicesGridViewHolder(private val binding: ItemGenericBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(services: List<Service>, title: String) {
            binding.categoryTitle.text = title
            binding.workerRecyclerView.layoutManager = GridLayoutManager(context, 2)
            binding.workerRecyclerView.adapter = GenericGridAdapter(services.take(4)) { service ->
                val intent = Intent(context, CategoryUsersActivity::class.java).apply {
                    putExtra("categoryName", service.name)
                    putExtra("service_id", service.service_id)
                }
                context.startActivity(intent)
            }

        }
    }

    inner class LoadMoreViewHolder(private val binding: ItemGenericBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.categoryTitle.text = "Load More"
            binding.categoryTitle.gravity = Gravity.CENTER
            binding.categoryTitle.setOnClickListener {
                val intent = Intent(context, AllServicesActivity::class.java)
                context.startActivity(intent)
            }
        }
    }

    inner class OrganizationViewHolder(private val binding: ItemGenericBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(items: List<Service>, title: String) {
            binding.categoryTitle.text = title
            binding.workerRecyclerView.layoutManager = GridLayoutManager(binding.root.context, 2)
            binding.workerRecyclerView.adapter = GenericGridAdapter(items) { service ->
                if (service.name == "About Us") {
                    val context = binding.root.context
                    val intent = Intent(context, AboutUsActivity::class.java)
                    context.startActivity(intent)
                }
            }
        }
    }


    inner class GBVHotlineViewHolder(private val binding: ItemGenericBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            binding.categoryTitle.visibility = GONE
            binding.categoryTitle.text = ""
            binding.workerRecyclerView.layoutManager = GridLayoutManager(context, 1)
            binding.workerRecyclerView.adapter = GenericGridAdapter(listOf(service)) {
                sendHelpRequest()
                val phoneNumber = "+254737333741"
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = "tel:$phoneNumber".toUri()
                }
                context.startActivity(dialIntent)
            }
            binding.categoryTitle.setOnClickListener {
                sendHelpRequest()
            }
        }

        private fun sendHelpRequest() {
            if (SupabaseData.isConfigured()) {
                val act = context as? FragmentActivity
                act?.lifecycleScope?.launch {
                    val ok = kotlin.runCatching { SupabaseData.insertHelpRequest() }.getOrElse { false }
                    withContext(Dispatchers.Main) {
                        val msg = if (ok) "Help request sent successfully!" else "Failed to send help request"
                        Toast.makeText(binding.root.context, msg, Toast.LENGTH_SHORT).show()
                    }
                } ?: sendHelpRequestLegacy()
            } else {
                sendHelpRequestLegacy()
            }
        }

        private fun sendHelpRequestLegacy() {
            val apiService = ServiceBuilder.buildService(ApiService::class.java)
            val call = apiService.helpRequest()
            call.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Log.d("MULTI-TYPE", "Get Help Response: $response")
                        Toast.makeText(binding.root.context, "Help request sent successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(binding.root.context, "Failed to send help request", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Toast.makeText(binding.root.context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
