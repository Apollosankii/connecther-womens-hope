package com.womanglobal.connecther.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.databinding.ItemOnboardingPageBinding

class OnboardingPagerAdapter(
    private val pages: List<OnboardingPage>,
) : RecyclerView.Adapter<OnboardingPagerAdapter.VH>() {

    class VH(val binding: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = pages[position]
        holder.binding.pageImage.setImageResource(page.imageRes)
        holder.binding.pageTitle.text = page.title
        holder.binding.pageBody.text = page.body
    }

    override fun getItemCount(): Int = pages.size
}

