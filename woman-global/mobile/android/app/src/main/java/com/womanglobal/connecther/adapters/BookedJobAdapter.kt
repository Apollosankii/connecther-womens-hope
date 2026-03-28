
package com.womanglobal.connecther.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Job

class BookedJobAdapter(private val jobs: List<Job>) : RecyclerView.Adapter<BookedJobAdapter.BookedJobViewHolder>() {

    inner class BookedJobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bookerTextView: TextView = itemView.findViewById(R.id.bookerTextView)
        val bookedPersonTextView: TextView = itemView.findViewById(R.id.bookedPersonTextView)
        val bookDateTextView: TextView = itemView.findViewById(R.id.bookDateTextView)

        fun bind(job: Job) {
            bookerTextView.text = "Booked by: ${job.client}"
            bookedPersonTextView.text = "Booked: ${job.provider}"
            bookDateTextView.text = "Date: ${job.location}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookedJobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booked_job, parent, false)
        return BookedJobViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookedJobViewHolder, position: Int) {
        holder.bind(jobs[position])
    }

    override fun getItemCount(): Int = jobs.size
}
