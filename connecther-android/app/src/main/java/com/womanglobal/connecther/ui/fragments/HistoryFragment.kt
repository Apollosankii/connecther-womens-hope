package com.womanglobal.connecther.ui.fragments

import ApiServiceFactory
import android.content.Context
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
import com.womanglobal.connecther.databinding.FragmentHistoryBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter
    private val completedJobs = mutableListOf<Job>()
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        val sharedPreferences = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isProvider = sharedPreferences.getBoolean("isProvider", false)

        setupRecyclerView(isProvider)

        return binding.root
    }

    private fun setupRecyclerView(isProvider: Boolean) {
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = HistoryAdapter(requireContext(), completedJobs, isProvider) { job ->
            showRatingDialog(job)
        }
        binding.historyRecyclerView.adapter = historyAdapter
    }

    private fun loadCompletedJobs() {
        if (SupabaseData.isConfigured()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val jobs = SupabaseData.getCompletedJobs()
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
        } else {
            apiService.getCompletedJobs().enqueue(object : Callback<List<Job>> {
                override fun onResponse(call: Call<List<Job>>, response: Response<List<Job>>) {
                    if (response.isSuccessful) {
                        completedJobs.clear()
                        completedJobs.addAll(response.body() ?: emptyList())

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
                    } else {
                        binding.noHistoryLayout.visibility = View.VISIBLE
                        binding.loadMoreButton.visibility = View.GONE
                        binding.historyRecyclerView.visibility = View.GONE
                    }
                }
                override fun onFailure(call: Call<List<Job>>, t: Throwable) {
                    Toast.makeText(requireContext(), "Check your Internet connection ", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showRatingDialog(job: Job) {
        val dialog = RatingDialogFragment(job) {
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
