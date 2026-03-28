package com.womanglobal.connecther.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.womanglobal.connecther.BuildConfig
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.databinding.ItemGenericGridBinding
import java.util.Locale

class GenericGridAdapter(
    private val items: List<Service>?,
    private val isFullSpan: Boolean = false,
    private val onClick: (Service) -> Unit
) : RecyclerView.Adapter<GenericGridAdapter.GenericItemViewHolder>() {

    private val safeItems: List<Service> get() = items ?: emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenericItemViewHolder {
        val binding = ItemGenericGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GenericItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GenericItemViewHolder, position: Int) {
        val list = safeItems
        if (position in list.indices) holder.bind(list[position], isFullSpan)
    }

    override fun getItemCount(): Int = safeItems.size

    inner class GenericItemViewHolder(private val binding: ItemGenericGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Service, isFullSpan: Boolean) {
            binding.itemTitle.text = item.name

            if (item.description.isNotBlank()) {
                binding.itemDescription.text = item.description
                binding.itemDescription.visibility = View.VISIBLE
            } else {
                binding.itemDescription.visibility = View.GONE
            }

            val price = item.min_price
            if (price != null && price > 0) {
                val formatted = if (price == price.toLong().toDouble())
                    "From KES %,d".format(price.toLong())
                else
                    "From KES %,.0f".format(price)
                binding.itemPrice.text = formatted
                binding.itemPrice.visibility = View.VISIBLE
                binding.itemPriceBadge.text = formatted.removePrefix("From ")
                binding.itemPriceBadge.visibility = View.VISIBLE
            } else {
                binding.itemPrice.visibility = View.GONE
                binding.itemPriceBadge.visibility = View.GONE
            }

            val imageUrl = when {
                item.pic.isBlank() -> null
                item.pic.startsWith("http") -> item.pic
                BuildConfig.SUPABASE_URL.isNotBlank() ->
                    "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/service_images/${item.pic.trimStart('/')}"
                else -> null
            }

            val nameLower = item.name.lowercase(Locale.getDefault())
            when (nameLower) {
                "mama fua" -> item.fallbackImageResId = R.mipmap.mama_fua
                "house manager" -> item.fallbackImageResId = R.mipmap.tutors
                "errand girl" -> item.fallbackImageResId = R.mipmap.house_keepers
                "care giver" -> item.fallbackImageResId = R.mipmap.caregivers
            }

            Glide.with(binding.itemImage.context)
                .load(imageUrl)
                .placeholder(item.fallbackImageResId ?: R.mipmap.generic_back3)
                .fallback(item.fallbackImageResId ?: R.mipmap.generic_back3)
                .centerCrop()
                .into(binding.itemImage)

            val layoutParams = binding.root.layoutParams as ViewGroup.LayoutParams
            layoutParams.width = if (isFullSpan) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            binding.root.layoutParams = layoutParams

            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
