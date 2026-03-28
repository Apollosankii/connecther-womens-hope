package com.womanglobal.connecther.ui.fragments

import ApiServiceFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import com.womanglobal.connecther.adapters.JobAdapter
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.databinding.FragmentJobsBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.JobDiffCallback
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class JobsFragment : Fragment() {
    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private lateinit var jobAdapter: JobAdapter
    private val jobList = mutableListOf<Job>()
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }

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

        if (SupabaseData.isConfigured()) {
            viewLifecycleOwner.lifecycleScope.launch {
                if (_binding == null) return@launch
                val newJobs = SupabaseData.getPendingJobs()
                if (_binding == null) return@launch

                val diffCallback = JobDiffCallback(jobList, newJobs)
                val diffResult = DiffUtil.calculateDiff(diffCallback)

                jobList.clear()
                jobList.addAll(newJobs)
                diffResult.dispatchUpdatesTo(jobAdapter)

                updateVisibility(newJobs)
            }
        } else {
            apiService.getPendingJobs().enqueue(object : Callback<List<Job>> {
                override fun onResponse(call: Call<List<Job>>, response: Response<List<Job>>) {
                    if (_binding == null) return

                    if (response.isSuccessful && response.body() != null) {
                        val newJobs = response.body()!!

                        val diffCallback = JobDiffCallback(jobList, newJobs)
                        val diffResult = DiffUtil.calculateDiff(diffCallback)

                        jobList.clear()
                        jobList.addAll(newJobs)
                        diffResult.dispatchUpdatesTo(jobAdapter)

                        updateVisibility(newJobs)
                    } else {
                        updateVisibility(emptyList())
                    }
                }

                override fun onFailure(call: Call<List<Job>>, t: Throwable) {
                    if (_binding == null) return
                    Toast.makeText(requireContext(), "Failed to load jobs", Toast.LENGTH_SHORT).show()
                    updateVisibility(emptyList())
                }
            })
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
        super.onDestroyView()
        _binding = null
    }
}
