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
import com.womanglobal.connecther.utils.JobDateUtils

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
        private val ratingYourSummary: TextView = itemView.findViewById(R.id.ratingYourSummary)
        private val ratingTheirSummary: TextView = itemView.findViewById(R.id.ratingTheirSummary)
        private val ratingWaitHint: TextView = itemView.findViewById(R.id.ratingWaitHint)
        private val rateJobButton: Button = itemView.findViewById(R.id.rateJobButton)

        fun bind(job: Job) {
            jobTitle.text = "Completed Job #${job.job_id}"
            if (isProvider) {
                clientName.text = context.getString(R.string.history_label_client, job.client)
            } else {
                clientName.text = context.getString(R.string.history_label_provider, job.provider)
            }
            serviceName.text = "Service: ${job.service}"
            jobPrice.text = "Price: Ksh ${job.price}"
            val completedFmt = JobDateUtils.formatForDisplay(job.completed_at)
            val startedFmt = JobDateUtils.formatForDisplay(job.started_at)
            jobDate.text = when {
                completedFmt.isNotEmpty() ->
                    context.getString(R.string.history_job_completed_at, completedFmt)
                startedFmt.isNotEmpty() ->
                    context.getString(R.string.history_job_started_fallback, startedFmt)
                else ->
                    context.getString(R.string.history_job_date_pending)
            }

            val needRate = !job.my_review_submitted
            val counterpartyFirst = if (isProvider) job.client.trim() else job.provider.trim()
            val counterpartyLabel = counterpartyFirst.ifBlank {
                context.getString(R.string.booking_request_party_unknown)
            }

            if (needRate) {
                rateJobButton.visibility = View.VISIBLE
                rateJobButton.text = context.getString(R.string.history_rate_counterparty, counterpartyLabel)
                rateJobButton.setOnClickListener { onRateClick(job) }
                jobRatingBar.visibility = View.GONE
                ratingYourSummary.visibility = View.GONE
            } else {
                rateJobButton.visibility = View.GONE
                jobRatingBar.visibility = View.VISIBLE
                jobRatingBar.rating = job.my_stars
                ratingYourSummary.visibility = View.VISIBLE
                ratingYourSummary.text = context.getString(R.string.history_your_rating, job.my_stars)
            }

            if (job.their_review_submitted && job.their_stars > 0f) {
                ratingTheirSummary.visibility = View.VISIBLE
                ratingTheirSummary.text =
                    context.getString(R.string.history_their_rating, job.their_stars)
                ratingWaitHint.visibility = View.GONE
            } else {
                ratingTheirSummary.visibility = View.GONE
                ratingWaitHint.visibility =
                    if (!needRate && !job.their_review_submitted) View.VISIBLE else View.GONE
                ratingWaitHint.text = context.getString(R.string.history_waiting_for_their_rating)
            }
        }
    }
}
