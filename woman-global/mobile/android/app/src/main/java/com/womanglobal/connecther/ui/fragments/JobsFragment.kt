package com.womanglobal.connecther.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womanglobal.connecther.ChatActivity
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.adapters.BookingsListAdapter
import com.womanglobal.connecther.adapters.BookingsListItem
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.databinding.FragmentJobsBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.util.JobSafetyScheduler
import com.womanglobal.connecther.utils.LocationMapUtils
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JobsFragment : Fragment() {
    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!

    private lateinit var bookingsAdapter: BookingsListAdapter

    private var pendingArrivalJobId: Int? = null
    private var pendingArrivalUri: Uri? = null

    private val takeArrivalPhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val jid = pendingArrivalJobId
            val uri = pendingArrivalUri
            pendingArrivalJobId = null
            pendingArrivalUri = null
            if (!ok || jid == null || uri == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = requireContext()
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(ctx, com.womanglobal.connecther.R.string.job_arrival_upload_failed, Toast.LENGTH_LONG)
                        .show()
                    return@launch
                }
                val path =
                    withContext(Dispatchers.IO) { SupabaseData.uploadJobSitePhoto(jid, bytes, "arrival.jpg") }
                if (path == null) {
                    Toast.makeText(ctx, com.womanglobal.connecther.R.string.job_arrival_upload_failed, Toast.LENGTH_LONG)
                        .show()
                    return@launch
                }
                val err = withContext(Dispatchers.IO) { SupabaseData.providerRecordJobArrival(jid, path) }
                if (err != null) {
                    Toast.makeText(ctx, "${ctx.getString(com.womanglobal.connecther.R.string.job_arrival_upload_failed)} ($err)", Toast.LENGTH_LONG)
                        .show()
                } else {
                    Toast.makeText(ctx, com.womanglobal.connecther.R.string.job_arrival_recorded, Toast.LENGTH_SHORT)
                        .show()
                }
                loadJobs()
            }
        }

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isAdded && _binding != null) {
                loadJobs()
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)

        bookingsAdapter = BookingsListAdapter(
            fragment = this,
            onJobsChanged = { loadJobs() },
            onProviderPickArrivalPhoto = { job ->
                pendingArrivalJobId = job.job_id
                val photoFile = java.io.File.createTempFile("arrival_${job.job_id}_", ".jpg", requireContext().cacheDir)
                val outUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    photoFile,
                )
                pendingArrivalUri = outUri
                takeArrivalPhoto.launch(outUri)
            },
            onAccept = { req ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val outcome = SupabaseData.acceptBookingRequest(req.id)
                    if (outcome.isSuccess) {
                        Toast.makeText(requireContext(), "Booking accepted", Toast.LENGTH_SHORT).show()
                        val chatCode = outcome.chatCode?.trim().orEmpty()
                        if (chatCode.isNotBlank()) {
                            val clientLabel = req.client_display ?: "Client"
                            android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Booking accepted")
                                .setMessage("Do you want to message $clientLabel now?")
                                .setPositiveButton("Message now") { _, _ ->
                                    startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                                        putExtra("chat_code", chatCode)
                                        putExtra("quote_id", outcome.quoteId ?: "")
                                        putExtra("peer_display_name", clientLabel)
                                        putExtra("providerName", clientLabel)
                                        putExtra("serviceName", "Service #${req.service_id ?: ""}")
                                    })
                                }
                                .setNegativeButton("Not now", null)
                                .show()
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            outcome.errorCode ?: "Failed to accept request",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    loadJobs()
                }
            },
            onDecline = { req ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val outcome = SupabaseData.declineBookingRequest(req.id)
                    if (outcome.isSuccess) {
                        Toast.makeText(requireContext(), "Booking declined", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), outcome.errorCode ?: "Failed to decline", Toast.LENGTH_SHORT).show()
                    }
                    loadJobs()
                }
            },
            onCancel = { req ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val outcome = SupabaseData.cancelBookingRequest(req.id)
                    if (outcome.isSuccess) {
                        Toast.makeText(requireContext(), "Request cancelled", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), outcome.errorCode ?: "Failed to cancel", Toast.LENGTH_SHORT).show()
                    }
                    loadJobs()
                }
            },
            onOpenMaps = { req ->
                LocationMapUtils.openInMaps(
                    requireContext(),
                    req.location_text.orEmpty(),
                    req.latitude,
                    req.longitude,
                )
            },
        )

        binding.jobsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.jobsRecyclerView.adapter = bookingsAdapter

        loadJobs()
        return binding.root
    }

    private fun isProviderSession(): Boolean =
        requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getBoolean("isProvider", false)

    private fun buildBookingsItems(
        jobs: List<Job>,
        completedJobs: List<Job>,
        requests: List<SupabaseData.MyBookingRequest>,
    ): List<BookingsListItem> {
        val out = ArrayList<BookingsListItem>()
        if (jobs.isNotEmpty()) {
            out.add(BookingsListItem.Section(R.string.jobs_section_active_jobs))
            jobs.forEach { out.add(BookingsListItem.JobCard(it)) }
        }
        if (completedJobs.isNotEmpty()) {
            out.add(BookingsListItem.Section(R.string.jobs_section_completed_jobs))
            completedJobs.forEach { out.add(BookingsListItem.JobCard(it)) }
        }
        if (requests.isNotEmpty()) {
            out.add(BookingsListItem.Section(R.string.jobs_section_booking_requests))
            requests.forEach { out.add(BookingsListItem.RequestCard(it)) }
        }
        return out
    }

    private fun loadJobs() {
        if (_binding == null) return

        if (!SupabaseData.isConfigured()) {
            binding.noJobsLayout.visibility = View.VISIBLE
            binding.jobsRecyclerView.visibility = View.GONE
            if (::bookingsAdapter.isInitialized) {
                bookingsAdapter.submitList(emptyList())
            }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null) return@launch

            val isProvider = isProviderSession()

            val online = NetworkStatus.isOnline(requireContext())
            val allRequests = if (online) {
                val fetched = SupabaseData.getMyBookingRequests()
                AppOfflineCache.writeBookingRequests(requireContext(), fetched)
                fetched
            } else {
                AppOfflineCache.readBookingRequests(requireContext()).orEmpty()
            }

            val clientRoleRequests = allRequests.filter {
                it.role.equals("client", ignoreCase = true) || it.role.equals("seeker", ignoreCase = true)
            }
            val providerPendingIncoming = allRequests.filter {
                it.role.equals("provider", ignoreCase = true) && it.status.equals("pending", ignoreCase = true)
            }
            val displayRequests = (
                if (isProvider) providerPendingIncoming + clientRoleRequests
                else clientRoleRequests
                )
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<SupabaseData.MyBookingRequest> {
                        when (it.status.lowercase()) {
                            "pending" -> 0
                            "accepted" -> 1
                            "declined" -> 2
                            "cancelled" -> 3
                            else -> 4
                        }
                    }.thenByDescending { it.id },
                )

            val newJobs = if (online) {
                val j = SupabaseData.getPendingJobs()
                AppOfflineCache.writePendingJobs(requireContext(), j)
                j
            } else {
                AppOfflineCache.readPendingJobs(requireContext()).orEmpty()
            }

            val completed = if (online) {
                val j = SupabaseData.getCompletedJobs()
                AppOfflineCache.writeCompletedJobs(requireContext(), j)
                j
            } else {
                AppOfflineCache.readCompletedJobs(requireContext()).orEmpty()
            }

            // If provider is back online after being offline, re-schedule any due check-ins.
            if (isProvider) {
                newJobs.filter { !it.i_am_client && it.work_started_at != null && it.safety_checkins_required }.forEach { job ->
                    JobSafetyScheduler.ensureDueScheduled(
                        requireContext().applicationContext,
                        job.job_id,
                        job.work_started_at,
                        intervalMin = job.safety_checkin_interval_min.coerceIn(15, 240),
                    )
                }
            }

            val items = buildBookingsItems(newJobs, completed, displayRequests)
            val wasEmpty = bookingsAdapter.currentList.isEmpty()

            showEmptyState(items.isEmpty())
            bookingsAdapter.submitList(items) {
                if (wasEmpty && items.isNotEmpty()) {
                    (activity as? HomeActivity)?.notifyTabScrollableContentChanged()
                }
            }
        }
    }

    private fun showEmptyState(empty: Boolean) {
        if (_binding == null) return
        if (empty) {
            binding.noJobsLayout.visibility = View.VISIBLE
            binding.jobsRecyclerView.visibility = View.GONE
        } else {
            binding.noJobsLayout.visibility = View.GONE
            binding.jobsRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
    }
}
