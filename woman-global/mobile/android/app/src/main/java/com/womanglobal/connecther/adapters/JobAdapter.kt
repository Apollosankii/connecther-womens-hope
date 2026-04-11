package com.womanglobal.connecther.adapters

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.R
import com.womanglobal.connecther.RatingDialogFragment
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.JobDateUtils
import com.womanglobal.connecther.utils.LocationMapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JobAdapter(
    private val fragment: Fragment,
    private val jobs: List<Job>,
    private val onJobCompleted: () -> Unit,
) : RecyclerView.Adapter<JobAdapter.JobViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(jobs[position])
    }

    override fun getItemCount(): Int = jobs.size

    inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val clientName: TextView = itemView.findViewById(R.id.clientName)
        private val providerName: TextView = itemView.findViewById(R.id.providerName)
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val priceText: TextView = itemView.findViewById(R.id.jobPrice)
        private val jobTimeline: TextView = itemView.findViewById(R.id.jobTimeline)
        private val jobCompleteHint: TextView = itemView.findViewById(R.id.jobCompleteHint)
        private val completeJobButton: MaterialButton = itemView.findViewById(R.id.completeTaskButton)

        fun bind(job: Job) {
            clientName.text = job.client
            providerName.text = job.provider
            serviceName.text = job.service
            val priceStr = if (job.price % 1.0 == 0.0) {
                job.price.toLong().toString()
            } else {
                job.price.toString()
            }
            priceText.text = itemView.context.getString(R.string.booking_detail_price_format, priceStr)

            val startedFmt = JobDateUtils.formatForDisplay(job.started_at)
            if (startedFmt.isNotEmpty()) {
                jobTimeline.visibility = View.VISIBLE
                jobTimeline.text = itemView.context.getString(R.string.job_timeline_started, startedFmt)
            } else {
                jobTimeline.visibility = View.GONE
            }

            val context = itemView.context
            val amSeekerOnThisJob = job.i_am_client

            jobCompleteHint.text = if (amSeekerOnThisJob) {
                context.getString(R.string.job_complete_seeker_hint)
            } else {
                context.getString(R.string.job_complete_provider_hint)
            }

            if (amSeekerOnThisJob) {
                completeJobButton.setText(R.string.job_action_complete)
                completeJobButton.setOnClickListener {
                    fragment.lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { SupabaseData.completeJob(job.job_id) }
                        if (ok) {
                            Toast.makeText(context, R.string.job_complete_success_toast, Toast.LENGTH_SHORT).show()
                            if (!job.rated && !job.my_review_submitted) {
                                offerOptionalPostCompleteRating(context, job)
                            } else {
                                onJobCompleted()
                            }
                        } else {
                            Toast.makeText(context, R.string.job_complete_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                completeJobButton.setText(R.string.job_action_view_location)
                completeJobButton.setOnClickListener {
                    openJobLocation(context, job.location)
                }
            }
        }

        private fun offerOptionalPostCompleteRating(context: Context, job: Job) {
            val title = if (job.i_am_client) R.string.jobs_rate_provider_title else R.string.jobs_rate_client_title
            val message = if (job.i_am_client) R.string.jobs_rate_provider_message else R.string.jobs_rate_client_message
            AlertDialog.Builder(context)
                .setTitle(context.getString(title))
                .setMessage(context.getString(message))
                .setPositiveButton(R.string.jobs_rate_now) { d, _ ->
                    d.dismiss()
                    RatingDialogFragment(job, isProvider = !job.i_am_client) {
                        onJobCompleted()
                    }.show(fragment.childFragmentManager, "RatingDialog")
                }
                .setNegativeButton(R.string.jobs_rate_not_now) { _, _ ->
                    onJobCompleted()
                }
                .setOnCancelListener {
                    onJobCompleted()
                }
                .show()
        }

        private fun openJobLocation(context: Context, rawLocation: String) {
            val trimmed = rawLocation.trim()
            val urlLine = trimmed.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
            if (urlLine != null) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlLine))) }
                return
            }
            LocationMapUtils.openInMaps(context, trimmed, null, null)
        }
    }
}
