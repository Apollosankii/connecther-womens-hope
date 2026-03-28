package com.womanglobal.connecther.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.ItemGenericGridBinding
import java.util.Locale

class GenericGridAdapter(
    private val items: List<Service>,
    private val isFullSpan: Boolean = false,
    private val onClick: (Service) -> Unit

) : RecyclerView.Adapter<GenericGridAdapter.GenericItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenericItemViewHolder {
        val binding = ItemGenericGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GenericItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GenericItemViewHolder, position: Int) {
        holder.bind(items[position], isFullSpan)
    }

    override fun getItemCount(): Int = items.size

    inner class GenericItemViewHolder(private val binding: ItemGenericGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Service, isFullSpan: Boolean) {
            binding.itemTitle.text = item.name
            binding.itemSubtitle.text = item.description?.ifBlank { "Trusted local provider available for this service." }
                ?: "Trusted local provider available for this service."
            binding.itemPrice.text = item.min_price?.let { "From KES ${it.toInt()}" } ?: "Price on request"

            if(item.name.lowercase(Locale.getDefault()) == "mama fua"){
                item.fallbackImageResId = R.mipmap.mama_fua
            }else if(item.name.lowercase(Locale.getDefault()) == "house manager"){
                item.fallbackImageResId = R.mipmap.tutors
            }else if(item.name.lowercase(Locale.getDefault()) == "errand girl"){
                item.fallbackImageResId = R.mipmap.house_keepers
            }else if(item.name.lowercase(Locale.getDefault()) == "care giver"){
                item.fallbackImageResId = R.mipmap.caregivers
            }

            val imageUrl = when {
                item.pic.startsWith("http", ignoreCase = true) -> item.pic
                item.pic.isBlank() -> null
                else -> "https://api.womanshope.org/static/${item.pic}"
            }

            Glide.with(binding.itemImage.context)
                .load(imageUrl)
                .placeholder(item.fallbackImageResId ?: R.mipmap.generic_back3)
                .fallback(item.fallbackImageResId ?: R.mipmap.generic_back3)
                .into(binding.itemImage)

            // Adjust item layout based on full span requirement
            val layoutParams = binding.root.layoutParams as ViewGroup.LayoutParams
            layoutParams.width = if (isFullSpan) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            binding.root.layoutParams = layoutParams

            binding.root.setOnClickListener { onClick(item) }
            binding.itemCta.setOnClickListener { onClick(item) }

        }
    }
}
