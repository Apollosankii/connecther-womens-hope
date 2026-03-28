package com.womanglobal.connecther

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.RateJobRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RatingDialogFragment(
    private val job: Job,
    private val onRatingSubmitted: () -> Unit
) : DialogFragment() {

    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_rating_dialog, null)
        val ratingBar: RatingBar = dialogView.findViewById(R.id.ratingBar)
        val reviewText: EditText = dialogView.findViewById(R.id.reviewText)

        return AlertDialog.Builder(requireContext()) // Standard AlertDialog
            .setTitle("Rate Job")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val rating = ratingBar.rating
                postRating(job.job_id, rating)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun postRating(jobId: Int, rating: Float) {
        val request = RateJobRequest(jobId, rating)
        apiService.rateJob(request).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                context?.let { ctx ->
                    if (response.isSuccessful) {
                        Toast.makeText(ctx, "Rating submitted", Toast.LENGTH_SHORT).show()
                        onRatingSubmitted()
                        dismissAllowingStateLoss() // Use this instead of dismiss() to avoid crashes
                    } else {
                        Toast.makeText(ctx, "Failed to submit rating", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}


