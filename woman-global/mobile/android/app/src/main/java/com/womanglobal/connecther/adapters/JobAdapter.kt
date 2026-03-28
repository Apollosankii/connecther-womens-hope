package com.womanglobal.connecther.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.PaymentOptionsActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.utils.ServiceBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class JobAdapter(
    private val jobs: List<Job>,
    private val onJobCompleted: () -> Unit
) : RecyclerView.Adapter<JobAdapter.JobViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = jobs[position]
        holder.bind(job)
    }

    override fun getItemCount(): Int = jobs.size

    inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val clientName: TextView = itemView.findViewById(R.id.clientName)
        private val providerName: TextView = itemView.findViewById(R.id.providerName)
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val priceText: TextView = itemView.findViewById(R.id.jobPrice)
        private val completeJobButton: Button = itemView.findViewById(R.id.completeTaskButton)

        fun bind(job: Job) {
            clientName.text = "Client: ${job.client}"
            providerName.text = "Provider: ${job.provider}"
            serviceName.text = "Service: ${job.service}"
            priceText.text = "Price: KES ${job.price}"

            // Retrieve isProvider from SharedPreferences
            val sharedPreferences = itemView.context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            val isProvider = sharedPreferences.getBoolean("isProvider", false) // Default to false
            val context = itemView.context


            if (isProvider) {
                completeJobButton.text = "View Location"
                completeJobButton.setOnClickListener {
                    val context = itemView.context
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(job.location))
                    context.startActivity(intent)
                }
            } else {
                completeJobButton.text = "Complete Job"
                completeJobButton.setOnClickListener {
                    completeJob(context,job.job_id)
                    val context = itemView.context
                    val intent = Intent(context, PaymentOptionsActivity::class.java)
                    intent.putExtra("price", "${job.price}")
                    context.startActivity(intent)
                }
            }
        }

        private fun completeJob(context: Context, jobId: Int) {
            val apiService = ServiceBuilder.buildService(ApiService::class.java)
            val call = apiService.completeJob(jobId)

            call.enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    Log.d("JOB COMPLETE ", "------------------> Job Response:  $response")
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Payment Request Made", Toast.LENGTH_SHORT).show()
                        onJobCompleted() // Notify JobsFragment to refresh the job list
                    } else {
                        Toast.makeText(context, "Failed to Complete Job", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

}
