package com.womanglobal.connecther.adapters

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.womanglobal.connecther.ProfileActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.User

class SearchAdapter(
    private val results: List<User>,
    private val query: String,
    private val serviceId: String // Pass the serviceId
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(results[position], query)
    }

    override fun getItemCount(): Int = results.size

    inner class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.userName)
        private val occupationTextView: TextView = itemView.findViewById(R.id.userOccupation)

        fun bind(user: User, query: String) {
            nameTextView.text = highlightQuery("${user.first_name} ${user.last_name}", query)
            occupationTextView.text = highlightQuery(user.county ?: "", query)

            // Set an OnClickListener to navigate to ProfileActivity with user details & serviceId
            itemView.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, ProfileActivity::class.java).apply {
                    putExtra("user", user) // Pass the user object
                    putExtra("service_id", serviceId) // Pass the serviceId
                }
                context.startActivity(intent)
            }

            // Load profile picture using Glide
            Glide.with(itemView.context)
                .load(user.pic)
                .placeholder(R.mipmap.woman_profile) // Fallback image
                .circleCrop() // Make the image circular
                .into(itemView.findViewById(R.id.userProfileImage))
        }

        private fun highlightQuery(text: String, query: String): SpannableString {
            val spannable = SpannableString(text)
            val startIndex = text.lowercase().indexOf(query.lowercase())
            if (startIndex >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.BLUE),
                    startIndex, startIndex + query.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    startIndex, startIndex + query.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return spannable
        }
    }
}
