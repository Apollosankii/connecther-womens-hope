package com.womanglobal.connecther.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womanglobal.connecther.RatingDialogFragment
import com.womanglobal.connecther.adapters.HistoryAdapter
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.databinding.FragmentHistoryBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter
    private val completedJobs = mutableListOf<Job>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        setupRecyclerView()
        return binding.root
    }

    private fun setupRecyclerView() {
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = HistoryAdapter(requireContext(), completedJobs) { job ->
            showRatingDialog(job)
        }
        binding.historyRecyclerView.adapter = historyAdapter
    }

    private fun loadCompletedJobs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val online = NetworkStatus.isOnline(ctx)
            val jobs = if (online) {
                val fetched = SupabaseData.getCompletedJobs()
                withContext(Dispatchers.IO) { AppOfflineCache.writeCompletedJobs(ctx, fetched) }
                fetched
            } else {
                AppOfflineCache.readCompletedJobs(ctx).orEmpty()
            }
            completedJobs.clear()
            completedJobs.addAll(jobs)

            if (completedJobs.isEmpty()) {
                binding.noHistoryLayout.visibility = View.VISIBLE
                binding.loadMoreButton.visibility = View.GONE
                binding.historyRecyclerView.visibility = View.GONE
            } else {
                binding.noHistoryLayout.visibility = View.GONE
                binding.loadMoreButton.visibility = View.VISIBLE
                binding.historyRecyclerView.visibility = View.VISIBLE
            }

            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun showRatingDialog(job: Job) {
        val dialog = RatingDialogFragment(job, isProvider = !job.i_am_client) {
            loadCompletedJobs()
        }
        dialog.show(childFragmentManager, "RatingDialog")
    }

    override fun onResume() {
        super.onResume()
        loadCompletedJobs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
