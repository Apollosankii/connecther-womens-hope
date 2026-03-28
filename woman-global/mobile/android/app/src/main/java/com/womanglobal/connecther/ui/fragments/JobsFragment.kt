package com.womanglobal.connecther.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import com.womanglobal.connecther.ChatActivity
import com.womanglobal.connecther.adapters.JobAdapter
import com.womanglobal.connecther.adapters.BookingRequestAdapter
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.databinding.FragmentJobsBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.JobDiffCallback
import com.womanglobal.connecther.utils.LocationMapUtils
import kotlinx.coroutines.launch

class JobsFragment : Fragment() {
    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private lateinit var jobAdapter: JobAdapter
    private val jobList = mutableListOf<Job>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isAdded && _binding != null) {
                loadJobs()
                refreshHandler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)

        jobAdapter = JobAdapter(jobList) {
            loadJobs()
        }
        binding.jobsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.jobsRecyclerView.adapter = jobAdapter

        loadJobs()

        return binding.root
    }

    private fun loadJobs() {
        if (_binding == null) return
        val sharedPreferences = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isProvider = sharedPreferences.getBoolean("isProvider", false)

        if (!SupabaseData.isConfigured()) {
            updateVisibility(emptyList())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null) return@launch

            val allRequests = SupabaseData.getMyBookingRequests()

            if (isProvider) {
                val pendingRequests = allRequests
                    .filter { it.role.equals("provider", ignoreCase = true) && it.status.equals("pending", ignoreCase = true) }
                    .sortedByDescending { it.id }

                if (pendingRequests.isNotEmpty()) {
                    binding.noJobsLayout.visibility = View.GONE
                    binding.jobsRecyclerView.visibility = View.VISIBLE
                    binding.jobsRecyclerView.adapter = BookingRequestAdapter(
                        pendingRequests,
                        onAccept = { req ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val outcome = SupabaseData.acceptBookingRequest(req.id)
                                if (outcome.isSuccess) {
                                    Toast.makeText(requireContext(), "Booking accepted", Toast.LENGTH_SHORT).show()
                                    if (!outcome.chatCode.isNullOrBlank()) {
                                        startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                                            putExtra("chat_code", outcome.chatCode)
                                            putExtra("quote_id", outcome.quoteId ?: "")
                                            putExtra("providerName", req.client_display ?: "Client")
                                            putExtra("serviceName", "Service #${req.service_id ?: ""}")
                                        })
                                    }
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        outcome.errorCode ?: "Failed to accept request",
                                        Toast.LENGTH_SHORT
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
                                    Toast.makeText(requireContext(), "Booking canceled", Toast.LENGTH_SHORT).show()
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
                    return@launch
                }
            } else {
                val seekerRequests = allRequests
                    .filter { it.role.equals("client", ignoreCase = true) || it.role.equals("seeker", ignoreCase = true) }
                    .sortedWith(
                        compareBy<SupabaseData.MyBookingRequest> {
                            when (it.status.lowercase()) {
                                "pending" -> 0
                                "accepted" -> 1
                                "declined" -> 2
                                "cancelled" -> 3
                                else -> 4
                            }
                        }.thenByDescending { it.id }
                    )
                if (seekerRequests.isNotEmpty()) {
                    binding.noJobsLayout.visibility = View.GONE
                    binding.jobsRecyclerView.visibility = View.VISIBLE
                    binding.jobsRecyclerView.adapter = BookingRequestAdapter(
                        seekerRequests,
                        onCancel = { req ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val outcome = SupabaseData.cancelBookingRequest(req.id)
                                if (outcome.isSuccess) {
                                    Toast.makeText(requireContext(), "Pending request cancelled", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), outcome.errorCode ?: "Failed to cancel", Toast.LENGTH_SHORT).show()
                                }
                                loadJobs()
                            }
                        },
                    )
                    return@launch
                }
            }

            // Default: pending jobs list.
            val newJobs = SupabaseData.getPendingJobs()
            val diffCallback = JobDiffCallback(jobList, newJobs)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            jobList.clear()
            jobList.addAll(newJobs)
            diffResult.dispatchUpdatesTo(jobAdapter)
            updateVisibility(newJobs)
        }
    }

    private fun updateVisibility(jobs: List<Job>) {
        if (_binding == null) return

        if (jobs.isEmpty()) {
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
}
