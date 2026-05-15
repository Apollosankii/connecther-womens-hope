package com.womanglobal.connecther

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RatingActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_JOB_ID = "job_id"
        private const val EXTRA_I_AM_CLIENT = "i_am_client"
        private const val EXTRA_RATEE_NAME = "ratee_name"
        private const val EXTRA_SERVICE_NAME = "service_name"

        fun createIntent(ctx: Context, job: Job): Intent =
            Intent(ctx, RatingActivity::class.java).apply {
                putExtra(EXTRA_JOB_ID, job.job_id)
                putExtra(EXTRA_I_AM_CLIENT, job.i_am_client)
                val ratee = if (job.i_am_client) job.provider.trim() else job.client.trim()
                putExtra(EXTRA_RATEE_NAME, ratee)
                putExtra(EXTRA_SERVICE_NAME, job.service)
            }
    }

    private var jobId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rating)

        jobId = intent.getIntExtra(EXTRA_JOB_ID, 0)
        val iAmClient = intent.getBooleanExtra(EXTRA_I_AM_CLIENT, true)
        val rateeName =
            intent.getStringExtra(EXTRA_RATEE_NAME).orEmpty().ifBlank {
                getString(R.string.booking_request_party_unknown)
            }

        if (jobId < 1) {
            Toast.makeText(this, R.string.rating_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.ratingButtonBack).setOnClickListener { finish() }

        findViewById<android.widget.TextView>(R.id.ratingName).text = rateeName
        findViewById<android.widget.TextView>(R.id.ratingRole).text =
            if (iAmClient) {
                getString(R.string.rating_role_caregiver)
            } else {
                getString(R.string.rating_role_client)
            }

        val avatar = findViewById<ImageView>(R.id.ratingAvatar)
        Glide.with(this).load(R.drawable.ic_avatar_neutral).circleCrop().into(avatar)

        val ratingBar = findViewById<RatingBar>(R.id.ratingBar)
        val reviewEdit = findViewById<EditText>(R.id.ratingComments)
        val submit = findViewById<AppCompatButton>(R.id.ratingSubmit)

        submit.setOnClickListener {
            val rating = ratingBar.rating
            if (rating < 0.5f) {
                Toast.makeText(this, R.string.rating_pick_stars, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val stars = rating.roundToInt().coerceIn(1, 5)
            val text = reviewEdit.text?.toString()
            submit.isEnabled = false
            lifecycleScope.launch {
                val result = SupabaseData.submitJobReview(jobId, stars, text)
                submit.isEnabled = true
                when {
                    result.ok -> {
                        Toast.makeText(this@RatingActivity, R.string.rating_submitted, Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                    result.err == "already_rated" ->
                        Toast.makeText(this@RatingActivity, R.string.rating_already_submitted, Toast.LENGTH_LONG).show()
                    else ->
                        Toast.makeText(this@RatingActivity, R.string.rating_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
