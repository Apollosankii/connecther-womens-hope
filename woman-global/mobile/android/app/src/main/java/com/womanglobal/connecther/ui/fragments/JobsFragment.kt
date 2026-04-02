package com.womanglobal.connecther.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.ChatActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.adapters.JobAdapter
import com.womanglobal.connecther.adapters.BookingRequestAdapter
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.databinding.FragmentJobsBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.LocationMapUtils
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.launch

/** Single-row section title for ConcatAdapter on the Bookings screen. */
private class JobsSectionTitleAdapter(@StringRes private val titleRes: Int) : RecyclerView.Adapter<JobsSectionTitleAdapter.VH>() {
    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            setText(titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.on_background))
            val h = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt()
            val v = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
            setPadding(h, v, h, v)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.setText(titleRes)
    }

    override fun getItemCount(): Int = 1
}

class JobsFragment : Fragment() {
    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
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
        binding.jobsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadJobs()
        return binding.root
    }

    private fun loadJobs() {
        if (_binding == null) return
        val sharedPreferences = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isProvider = sharedPreferences.getBoolean("isProvider", false)

        if (!SupabaseData.isConfigured()) {
            showEmptyState(true)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null) return@launch

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

            val concat = ConcatAdapter()
            if (newJobs.isNotEmpty()) {
                concat.addAdapter(JobsSectionTitleAdapter(R.string.jobs_section_active_jobs))
                concat.addAdapter(
                    JobAdapter(this@JobsFragment, newJobs.toList()) {
                        loadJobs()
                    },
                )
            }
            if (displayRequests.isNotEmpty()) {
                concat.addAdapter(JobsSectionTitleAdapter(R.string.jobs_section_booking_requests))
                concat.addAdapter(
                    BookingRequestAdapter(
                        displayRequests,
                        onAccept = if (isProvider) { req ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val outcome = SupabaseData.acceptBookingRequest(req.id)
                                if (outcome.isSuccess) {
                                    Toast.makeText(requireContext(), "Booking accepted", Toast.LENGTH_SHORT).show()
                                    if (!outcome.chatCode.isNullOrBlank()) {
                                        startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
                                            putExtra("chat_code", outcome.chatCode)
                                            putExtra("quote_id", outcome.quoteId ?: "")
                                            val clientLabel = req.client_display ?: "Client"
                                            putExtra("peer_display_name", clientLabel)
                                            putExtra("providerName", clientLabel)
                                            putExtra("serviceName", "Service #${req.service_id ?: ""}")
                                        })
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
                        } else null,
                        onDecline = if (isProvider) { req ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val outcome = SupabaseData.declineBookingRequest(req.id)
                                if (outcome.isSuccess) {
                                    Toast.makeText(requireContext(), "Booking declined", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), outcome.errorCode ?: "Failed to decline", Toast.LENGTH_SHORT).show()
                                }
                                loadJobs()
                            }
                        } else null,
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
                    ),
                )
            }

            binding.jobsRecyclerView.adapter = concat
            showEmptyState(newJobs.isEmpty() && displayRequests.isEmpty())
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
}
