package com.womanglobal.connecther.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

        fun bind(item: Service, @Suppress("UNUSED_PARAMETER") isFullSpan: Boolean) {
            val ctx = binding.root.context
            binding.itemTitle.text = item.name
            binding.itemSubtitle.text = item.description?.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.marketplace_service_default_description)

            val priceInt = item.min_price?.toInt()
            if (priceInt != null && priceInt > 0) {
                binding.itemPriceBadge.text = ctx.getString(R.string.marketplace_price_badge_format, priceInt)
                binding.itemPriceBadge.visibility = View.VISIBLE
                binding.itemPrice.text = ctx.getString(R.string.marketplace_from_price_format, priceInt)
                binding.itemPrice.setTextColor(ContextCompat.getColor(ctx, R.color.accent_color))
            } else {
                binding.itemPriceBadge.visibility = View.GONE
                binding.itemPrice.text = ctx.getString(R.string.marketplace_price_on_request)
                binding.itemPrice.setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_variant))
            }

            applyNamedFallback(item)

            val imageUrl = when {
                item.pic.startsWith("http", ignoreCase = true) -> item.pic
                item.pic.isBlank() -> null
                else -> null
            }

            Glide.with(binding.itemImage.context)
                .load(imageUrl)
                .placeholder(item.fallbackImageResId ?: R.mipmap.generic_back3)
                .fallback(item.fallbackImageResId ?: R.mipmap.generic_back3)
                .centerCrop()
                .into(binding.itemImage)

            binding.root.layoutParams = binding.root.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }

            binding.root.setOnClickListener { onClick(item) }
        }

        private fun applyNamedFallback(item: Service) {
            when (item.name.lowercase(Locale.getDefault())) {
                "mama fua" -> item.fallbackImageResId = R.mipmap.mama_fua
                "house manager" -> item.fallbackImageResId = R.mipmap.tutors
                "errand girl" -> item.fallbackImageResId = R.mipmap.house_keepers
                "care giver", "caregiver" -> item.fallbackImageResId = R.mipmap.caregivers
                "tailor" -> item.fallbackImageResId = R.mipmap.generic_back3
            }
        }
    }
}
