package com.womanglobal.connecther.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Job

class HistoryAdapter(
    private val context: Context,
    private val jobs: List<Job>,
    private val isProvider: Boolean,
    private val onRateClick: (Job) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_completed_job, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val job = jobs[position]
        holder.bind(job)
    }

    override fun getItemCount(): Int = jobs.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val jobTitle: TextView = itemView.findViewById(R.id.jobTitle)
        private val clientName: TextView = itemView.findViewById(R.id.clientName)
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val jobPrice: TextView = itemView.findViewById(R.id.jobPrice)
        private val jobDate: TextView = itemView.findViewById(R.id.jobDate)
        private val jobRatingBar: RatingBar = itemView.findViewById(R.id.jobRatingBar)
        private val rateJobButton: Button = itemView.findViewById(R.id.rateJobButton)

        fun bind(job: Job) {
            jobTitle.text = "Completed Job #${job.job_id}"
            clientName.text = "Client: ${job.client}"
            serviceName.text = "Service: ${job.service}"
            jobPrice.text = "Price: Ksh ${job.price}"
            jobDate.text = "Date: 2024-02-28"

            if (isProvider) {
                jobRatingBar.visibility = View.VISIBLE
                jobRatingBar.rating = job.score
                rateJobButton.visibility = View.GONE
            } else {
                if (job.rated) {
                    jobRatingBar.visibility = View.VISIBLE
                    jobRatingBar.rating = job.score
                    rateJobButton.visibility = View.GONE
                } else {
                    jobRatingBar.visibility = View.GONE
                    rateJobButton.visibility = View.VISIBLE
                    rateJobButton.setOnClickListener { onRateClick(job) }
                }
            }
        }
    }
}


