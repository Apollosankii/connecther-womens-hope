package com.womanglobal.connecther.adapters

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.AboutUsActivity
import com.womanglobal.connecther.AllServicesActivity
import com.womanglobal.connecther.CategoryUsersActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.SearchActivity
import com.womanglobal.connecther.data.Category
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.ItemGenericBinding
import com.womanglobal.connecther.databinding.ItemTopBinding
import androidx.viewpager2.widget.ViewPager2

class MultiTypeAdapter(
    private val categories: List<Category>,
    private val context: Context,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TOP = 0
        private const val VIEW_TYPE_SERVICES_GRID = 1
        private const val VIEW_TYPE_LOAD_MORE = 2
        private const val VIEW_TYPE_ORGANIZATION = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> VIEW_TYPE_TOP
            1 -> VIEW_TYPE_SERVICES_GRID
            2 -> VIEW_TYPE_LOAD_MORE
            3 -> VIEW_TYPE_ORGANIZATION
            else -> throw IllegalArgumentException("Invalid position: $position")
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
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TopViewHolder -> holder.bind()
            is ServicesGridViewHolder -> holder.bind(categories[0].services, categories[0].name)
            is LoadMoreViewHolder -> holder.bind()
            is OrganizationViewHolder -> if (categories.size > 1) {
                holder.bind(categories[1].services, categories[1].name)
            }
        }
    }

    override fun getItemCount(): Int = 4


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
                    handler.postDelayed(this, 3000)
                }
            }
            handler.postDelayed(runnable, 3000)

            binding.searchField.setOnClickListener {
                val intent = Intent(context, SearchActivity::class.java)
                context.startActivity(intent)
            }

            binding.audioSearchButton.setOnClickListener {
                val intent = Intent(context, SearchActivity::class.java).apply {
                    putExtra("voiceSearch", true)
                }
                context.startActivity(intent)
            }
        }
    }


    inner class ServicesGridViewHolder(private val binding: ItemGenericBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(services: List<Service>, title: String) {
            binding.categoryTitle.text = title
            binding.categoryTitle.visibility = if (title.isBlank()) View.GONE else View.VISIBLE
            binding.workerRecyclerView.layoutManager = GridLayoutManager(context, 2)
            binding.workerRecyclerView.adapter = GenericGridAdapter(services.take(3)) { service ->
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
}
