package com.womanglobal.connecther.ui.fragments

import ApiServiceFactory
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.womanglobal.connecther.BookJobActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.adapters.BookedJobAdapter
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.databinding.FragmentBookNowBinding
import com.womanglobal.connecther.utils.UIHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookNowFragment : Fragment() {
    private var _binding: FragmentBookNowBinding? = null
    private val binding get() = _binding!!

    private fun showBookingDialog(date: Long) {
        // Inflate the dialog layout and initialize views
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_book_job, null)
        val bookedJobsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.bookedJobsRecyclerView)
        val bookJobButton = dialogView.findViewById<Button>(R.id.bookJobButton)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Fetch jobs for the selected date
        val apiService = ApiServiceFactory.createApiService()
        apiService.getJobsForDate(date).enqueue(object : Callback<List<Job>> {
            override fun onResponse(call: Call<List<Job>>, response: Response<List<Job>>) {
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    // Display booked jobs in RecyclerView
                    val jobs = response.body()!!
                    bookedJobsRecyclerView.apply {
                        visibility = View.VISIBLE
                        layoutManager = LinearLayoutManager(context)
                        adapter = BookedJobAdapter(jobs)
                    }
                } else {
                    // Hide RecyclerView if no jobs are booked
                    bookedJobsRecyclerView.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call<List<Job>>, t: Throwable) {
                Toast.makeText(context, "Failed to load jobs: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // "Book Job" button click listener
        bookJobButton.setOnClickListener {
            val intent = Intent(context, BookJobActivity::class.java).apply {
                putExtra("selectedDate", date)
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
        val apiService = ApiServiceFactory.createApiService()
        apiService.getBookedDates().enqueue(object : Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                if (response.isSuccessful && response.body() != null) {
                    val bookedDateStrings = response.body()!!
                    val bookedDates = bookedDateStrings.map { parseDate(it) }
                    applyBookedDatesDecorator(bookedDates)
                } else {
                    UIHelper.showToastLong(context, "Failed to load booked dates")
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                UIHelper.showToastLong(context, "Error: ${t.message}")
            }
        })
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
