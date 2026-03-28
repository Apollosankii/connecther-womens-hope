package com.womanglobal.connecther.ui.fragments

import BookedDateDecorator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.womanglobal.connecther.BookJobActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.adapters.BookedJobAdapter
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.databinding.FragmentBookNowBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.UIHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BookNowFragment : Fragment() {
    private var _binding: FragmentBookNowBinding? = null
    private val binding get() = _binding!!
    private val serviceId: String? by lazy { arguments?.getString("service_id") }

    private fun showBookingDialog(date: Long) {
        // Inflate the dialog layout and initialize views
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_book_job, null)
        val bookedJobsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.bookedJobsRecyclerView)
        val bookJobButton = dialogView.findViewById<Button>(R.id.bookJobButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Fetch booked jobs for the selected date (Supabase-backed).
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDay = sdf.format(Date(date))

        // booking_requests/job.location are stored as text; we match by date prefix.
        lifecycleScope.launchWhenResumed {
            val pending = runCatching { SupabaseData.getPendingJobs() }.getOrDefault(emptyList())
            val completed = runCatching { SupabaseData.getCompletedJobs() }.getOrDefault(emptyList())
            val jobsForDay = (pending + completed).filter { job ->
                job.location?.take(10)?.equals(selectedDay, ignoreCase = true) == true
            }

            bookedJobsRecyclerView.apply {
                visibility = if (jobsForDay.isNotEmpty()) View.VISIBLE else View.GONE
                if (jobsForDay.isNotEmpty()) {
                    layoutManager = LinearLayoutManager(context)
                    adapter = BookedJobAdapter(jobsForDay)
                }
            }
        }

        // "Book Job" button click listener
        bookJobButton.setOnClickListener {
            val intent = Intent(context, BookJobActivity::class.java).apply {
                putExtra("selectedDate", date)
                serviceId?.let { putExtra("service_id", it) }
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBookNowBinding.inflate(inflater, container, false)

        loadBookedDates()

        binding.bookNowButton.setOnClickListener {
            val selectedDate = binding.calendarView.selectedDate
            if (selectedDate != null) {
                bookDate(selectedDate)
            } else {
                Toast.makeText(context, "Please select a date", Toast.LENGTH_SHORT).show()
            }
        }



        binding.calendarView.setOnDateChangedListener { _, date, _ ->
            val selectedDate = date.date.time
            showBookingDialog(selectedDate)
        }



        return binding.root
    }

    private fun loadBookedDates() {
        if (!SupabaseData.isConfigured()) {
            UIHelper.showToastLong(context, "Supabase not configured; can't load booked dates.")
            return
        }

        lifecycleScope.launchWhenResumed {
            val pending = runCatching { SupabaseData.getPendingJobs() }.getOrDefault(emptyList())
            val completed = runCatching { SupabaseData.getCompletedJobs() }.getOrDefault(emptyList())
            val all = pending + completed

            val bookedDates = all.mapNotNull { job ->
                val dayStr = job.location?.take(10) // expecting 'yyyy-MM-dd'
                if (dayStr.isNullOrBlank()) null else parseDate(dayStr)
            }.distinctBy { it.time }

            applyBookedDatesDecorator(bookedDates)
        }
    }

    private fun applyBookedDatesDecorator(bookedDates: List<Calendar>) {
        val decorator = BookedDateDecorator(bookedDates)
        binding.calendarView.addDecorator(decorator)
    }

    private fun bookDate(date: CalendarDay) {
        Toast.makeText(context, "Booking date: ${date.date}", Toast.LENGTH_SHORT).show()
        // Add booking logic here
    }

    private fun parseDate(dateStr: String): Calendar {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr)
        return Calendar.getInstance().apply {
            if (date != null) {
                time = date
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
