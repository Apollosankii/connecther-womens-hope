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
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.R
import com.womanglobal.connecther.RatingDialogFragment
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.util.JobSafetyScheduler
import com.womanglobal.connecther.utils.JobDateUtils
import com.womanglobal.connecther.utils.LocationMapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class BookingsListItem {
    data class Section(@StringRes val titleRes: Int) : BookingsListItem()
    data class JobCard(val job: Job) : BookingsListItem()
    data class RequestCard(val req: SupabaseData.MyBookingRequest) : BookingsListItem()
}

private object BookingsListDiff : DiffUtil.ItemCallback<BookingsListItem>() {
    override fun areItemsTheSame(a: BookingsListItem, b: BookingsListItem): Boolean {
        return when {
            a is BookingsListItem.Section && b is BookingsListItem.Section -> a.titleRes == b.titleRes
            a is BookingsListItem.JobCard && b is BookingsListItem.JobCard -> a.job.job_id == b.job.job_id
            a is BookingsListItem.RequestCard && b is BookingsListItem.RequestCard -> a.req.id == b.req.id
            else -> false
        }
    }

    override fun areContentsTheSame(a: BookingsListItem, b: BookingsListItem): Boolean = a == b
}

/**
 * Single list for the Bookings tab: section headers, active jobs, booking requests.
 * Used with [ListAdapter.submitList] so refreshes do not reset scroll position.
 */
class BookingsListAdapter(
    private val fragment: Fragment,
    private val onJobsChanged: () -> Unit,
    private val onAccept: (SupabaseData.MyBookingRequest) -> Unit,
    private val onDecline: (SupabaseData.MyBookingRequest) -> Unit,
    private val onCancel: (SupabaseData.MyBookingRequest) -> Unit,
    private val onOpenMaps: (SupabaseData.MyBookingRequest) -> Unit,
    private val onProviderPickArrivalPhoto: ((Job) -> Unit)? = null,
) : ListAdapter<BookingsListItem, RecyclerView.ViewHolder>(BookingsListDiff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is BookingsListItem.Section -> VT_SECTION
        is BookingsListItem.JobCard -> VT_JOB
        is BookingsListItem.RequestCard -> VT_REQUEST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VT_SECTION -> {
                val tv = TextView(parent.context).apply {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.on_background))
                    val h = (20 * resources.displayMetrics.density).toInt()
                    val v = (12 * resources.displayMetrics.density).toInt()
                    setPadding(h, v, h, v)
                }
                SectionVH(tv)
            }
            VT_JOB -> JobVH(inflater.inflate(R.layout.item_job, parent, false))
            VT_REQUEST -> RequestVH(inflater.inflate(R.layout.item_booking_request_action_row, parent, false))
            else -> error("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is BookingsListItem.Section -> (holder as SectionVH).bind(item.titleRes)
            is BookingsListItem.JobCard -> (holder as JobVH).bind(item.job)
            is BookingsListItem.RequestCard -> (holder as RequestVH).bind(item.req)
        }
    }

    private inner class SectionVH(private val text: TextView) : RecyclerView.ViewHolder(text) {
        fun bind(@StringRes titleRes: Int) {
            text.setText(titleRes)
        }
    }

    private inner class JobVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val clientName: TextView = itemView.findViewById(R.id.clientName)
        private val providerName: TextView = itemView.findViewById(R.id.providerName)
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val priceText: TextView = itemView.findViewById(R.id.jobPrice)
        private val jobTimeline: TextView = itemView.findViewById(R.id.jobTimeline)
        private val jobCompleteHint: TextView = itemView.findViewById(R.id.jobCompleteHint)
        private val jobLocationExtra: TextView = itemView.findViewById(R.id.jobLocationExtra)
        private val jobSafetyStatus: TextView = itemView.findViewById(R.id.jobSafetyStatus)
        private val arrivalPhotoButton: MaterialButton = itemView.findViewById(R.id.buttonJobArrivalPhoto)
        private val startWorkButton: MaterialButton = itemView.findViewById(R.id.buttonJobStartWork)
        private val completeJobButton: MaterialButton = itemView.findViewById(R.id.completeTaskButton)

        fun bind(job: Job) {
            val context = itemView.context
            clientName.text = job.client
            providerName.text = job.provider
            serviceName.text = job.service
            val priceStr = if (job.price % 1.0 == 0.0) {
                job.price.toLong().toString()
            } else {
                job.price.toString()
            }
            priceText.text = context.getString(R.string.booking_detail_price_format, priceStr)

            val startedFmt = JobDateUtils.formatForDisplay(job.started_at)
            if (startedFmt.isNotEmpty()) {
                jobTimeline.visibility = View.VISIBLE
                jobTimeline.text = context.getString(R.string.job_timeline_started, startedFmt)
            } else {
                jobTimeline.visibility = View.GONE
            }

            val amSeekerOnThisJob = job.i_am_client
            jobCompleteHint.text = if (amSeekerOnThisJob) {
                context.getString(R.string.job_complete_seeker_hint)
            } else {
                context.getString(R.string.job_complete_provider_hint)
            }

            val extraDisp = job.location_detail_display.trim()
            if (extraDisp.isNotEmpty()) {
                jobLocationExtra.visibility = View.VISIBLE
                jobLocationExtra.text = extraDisp
            } else {
                jobLocationExtra.visibility = View.GONE
            }

            val photoDone = !job.site_photo_path.isNullOrBlank()
            val workOn = job.work_started_at != null
            val isCompleted = job.completed_at != null
            val safetyRequired = job.safety_checkins_required
            val intervalMin = job.safety_checkin_interval_min.coerceIn(15, 240)
            if (amSeekerOnThisJob) {
                jobSafetyStatus.visibility = View.VISIBLE
                jobSafetyStatus.text = when {
                    workOn -> context.getString(R.string.job_safety_seeker_active)
                    photoDone -> context.getString(R.string.job_safety_seeker_arrived)
                    else -> ""
                }
                jobSafetyStatus.isVisible = jobSafetyStatus.text.isNotEmpty()
            } else {
                jobSafetyStatus.isVisible = workOn
                jobSafetyStatus.text = if (workOn) context.getString(R.string.job_safety_provider_active) else ""
            }

            val pickArrival = onProviderPickArrivalPhoto
            if (!amSeekerOnThisJob && pickArrival != null) {
                arrivalPhotoButton.isVisible = !isCompleted && !photoDone
                arrivalPhotoButton.setOnClickListener { pickArrival(job) }
                startWorkButton.isVisible = !isCompleted && safetyRequired && !workOn
                startWorkButton.setOnClickListener {
                    fragment.lifecycleScope.launch {
                        val err = withContext(Dispatchers.IO) { SupabaseData.providerStartJobWork(job.job_id) }
                        if (err == null) {
                            Toast.makeText(context, R.string.job_work_started, Toast.LENGTH_LONG).show()
                            JobSafetyScheduler.scheduleFirstHour(context.applicationContext, job.job_id, intervalMin = intervalMin)
                            onJobsChanged()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.job_start_work_failed, err),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            } else {
                arrivalPhotoButton.isVisible = false
                startWorkButton.isVisible = false
            }

            if (amSeekerOnThisJob) {
                if (isCompleted) {
                    completeJobButton.text = context.getString(R.string.history_job_completed_at, job.completed_at ?: "")
                    completeJobButton.isEnabled = false
                    completeJobButton.alpha = 0.6f
                    completeJobButton.setOnClickListener(null)
                } else {
                    completeJobButton.setText(R.string.job_action_complete)
                    completeJobButton.isEnabled = true
                    completeJobButton.alpha = 1f
                    completeJobButton.setOnClickListener {
                        fragment.lifecycleScope.launch {
                            val ok = withContext(Dispatchers.IO) { SupabaseData.completeJob(job.job_id) }
                            if (ok) {
                                Toast.makeText(context, R.string.job_complete_success_toast, Toast.LENGTH_SHORT).show()
                                JobSafetyScheduler.cancelForJob(context.applicationContext, job.job_id)
                                if (!job.rated && !job.my_review_submitted) {
                                    offerOptionalPostCompleteRating(context, job)
                                } else {
                                    onJobsChanged()
                                }
                            } else {
                                Toast.makeText(context, R.string.job_complete_failed, Toast.LENGTH_LONG).show()
                            }
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
                        onJobsChanged()
                    }.show(fragment.childFragmentManager, "RatingDialog")
                }
                .setNegativeButton(R.string.jobs_rate_not_now) { _, _ ->
                    onJobsChanged()
                }
                .setOnCancelListener {
                    onJobsChanged()
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

    private inner class RequestVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.bookingTitle)
        private val status: TextView = view.findViewById(R.id.bookingStatus)
        private val requestId: TextView = view.findViewById(R.id.bookingRequestId)
        private val details: TextView = view.findViewById(R.id.bookingDetails)
        private val actionsPrimary: View = view.findViewById(R.id.actionsRowPrimary)
        private val actionsSecondary: View = view.findViewById(R.id.actionsRowSecondary)
        private val accept: MaterialButton = view.findViewById(R.id.btnAccept)
        private val decline: MaterialButton = view.findViewById(R.id.btnDecline)
        private val cancel: MaterialButton = view.findViewById(R.id.btnCancel)
        private val maps: MaterialButton = view.findViewById(R.id.btnMaps)

        fun bind(req: SupabaseData.MyBookingRequest) {
            val ctx = itemView.context
            val otherParty = if (req.role == "provider") req.client_display else req.provider_display
            val name = otherParty?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.booking_request_party_unknown)
            title.text = ctx.getString(R.string.booking_request_title_with_name, name)
            status.applyBookingStatusUi(ctx, req.status)
            requestId.text = ctx.getString(R.string.booking_request_id_format, req.id)

            val lines = buildList {
                req.service_id?.let { add(ctx.getString(R.string.booking_detail_service, it.toString())) }
                req.location_text?.takeIf { it.isNotBlank() }?.let { add(it) }
                req.location_extra?.takeIf { le -> !le.isEmpty() }?.let { le ->
                    val text = formatLocationExtraForDisplay(le)
                    if (text.isNotBlank()) add(text)
                }
                req.maps_url?.takeIf { it.isNotBlank() }?.let { add(it) }
                req.proposed_price?.let { p ->
                    val pStr = if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()
                    add(ctx.getString(R.string.booking_detail_price_format, pStr))
                }
                req.message?.takeIf { it.isNotBlank() }?.let { add(it) }
                req.created_at?.takeIf { it.isNotBlank() }?.let { raw ->
                    val short = raw.replace("T", " ").take(16).trim()
                    if (short.isNotEmpty()) add(short)
                }
            }
            details.text = lines.joinToString("\n")
            details.isVisible = lines.isNotEmpty()

            val pending = req.status.equals("pending", ignoreCase = true)
            val isIncomingAsProvider = req.role.equals("provider", ignoreCase = true)
            val isOutgoingAsClient =
                req.role.equals("client", ignoreCase = true) || req.role.equals("seeker", ignoreCase = true)
            val isProviderNow = fragment.requireContext()
                .getSharedPreferences("user_session", Context.MODE_PRIVATE)
                .getBoolean("isProvider", false)
            val showAccept = pending && isIncomingAsProvider && isProviderNow
            val showDecline = pending && isIncomingAsProvider && isProviderNow
            accept.isVisible = showAccept
            decline.isVisible = showDecline
            actionsPrimary.isVisible = showAccept || showDecline

            val showCancel = pending && isOutgoingAsClient
            val showMaps = true
            cancel.isVisible = showCancel
            maps.isVisible = showMaps
            actionsSecondary.isVisible = showCancel || showMaps

            accept.setOnClickListener { onAccept?.invoke(req) }
            decline.setOnClickListener { onDecline?.invoke(req) }
            cancel.setOnClickListener { onCancel?.invoke(req) }
            maps.setOnClickListener { onOpenMaps?.invoke(req) }
        }
    }

    private companion object {
        const val VT_SECTION = 0
        const val VT_JOB = 1
        const val VT_REQUEST = 2
    }
}

private fun formatLocationExtraForDisplay(le: JsonObject): String =
    le.entries.joinToString("\n") { e ->
        "${e.key}: ${e.value}"
    }

private fun TextView.applyBookingStatusUi(context: Context, raw: String) {
    val key = raw.lowercase()
    val (bg, fg) = when {
        key == "pending" -> R.drawable.bg_booking_status_pending to R.color.booking_status_pending_text
        key == "accepted" -> R.drawable.bg_booking_status_accepted to R.color.booking_status_accepted_text
        key == "declined" -> R.drawable.bg_booking_status_declined to R.color.booking_status_declined_text
        key == "cancelled" || key == "canceled" -> R.drawable.bg_booking_status_cancelled to R.color.booking_status_cancelled_text
        else -> R.drawable.bg_booking_status_neutral to R.color.on_surface_variant
    }
    setBackgroundResource(bg)
    setTextColor(ContextCompat.getColor(context, fg))
    text = raw.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
}
