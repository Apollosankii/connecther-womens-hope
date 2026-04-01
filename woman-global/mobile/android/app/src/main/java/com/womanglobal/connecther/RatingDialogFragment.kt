package com.womanglobal.connecther

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RatingDialogFragment(
    private val job: Job,
    private val isProvider: Boolean,
    private val onRatingSubmitted: () -> Unit,
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_rating_dialog, null)
        val ratingBar: RatingBar = dialogView.findViewById(R.id.ratingBar)
        val reviewEdit: EditText = dialogView.findViewById(R.id.reviewText)
        val titleText: TextView = dialogView.findViewById(R.id.titleText)

        val rateeName = if (isProvider) job.client.trim() else job.provider.trim()
        val displayName = rateeName.ifBlank { getString(R.string.booking_request_party_unknown) }
        titleText.text = getString(R.string.rating_dialog_title, displayName)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.rating_submit, null)
            .setNegativeButton(R.string.rating_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rating = ratingBar.rating
                if (rating < 0.5f) {
                    Toast.makeText(requireContext(), R.string.rating_pick_stars, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val stars = rating.roundToInt().coerceIn(1, 5)
                val text = reviewEdit.text?.toString()
                val submitBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                submitBtn.isEnabled = false
                lifecycleScope.launch {
                    val result = SupabaseData.submitJobReview(job.job_id, stars, text)
                    submitBtn.isEnabled = true
                    val ctx = context ?: return@launch
                    when {
                        result.ok -> {
                            Toast.makeText(ctx, R.string.rating_submitted, Toast.LENGTH_SHORT).show()
                            onRatingSubmitted()
                            dialog.dismiss()
                        }
                        result.err == "already_rated" ->
                            Toast.makeText(ctx, R.string.rating_already_submitted, Toast.LENGTH_LONG).show()
                        else ->
                            Toast.makeText(ctx, R.string.rating_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        return dialog
    }
}
