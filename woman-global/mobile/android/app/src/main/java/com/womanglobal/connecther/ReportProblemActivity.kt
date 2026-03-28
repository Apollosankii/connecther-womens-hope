package com.womanglobal.connecther

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.womanglobal.connecther.data.ProblemReport
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportProblemActivity : AppCompatActivity() {

    private lateinit var problemDescriptionInput: EditText
    private lateinit var sendReportButton: Button
    private val apiService = ApiServiceFactory.createApiService() // Use your mock service for testing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_problem)

        problemDescriptionInput = findViewById(R.id.problemDescriptionInput)
        sendReportButton = findViewById(R.id.sendReportButton)

        sendReportButton.setOnClickListener {
            val description = problemDescriptionInput.text.toString()
            if (description.isBlank()) {
                Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            } else {
                sendProblemReport(description)
            }
        }
    }

    private fun sendProblemReport(description: String) {
        val problemReport = ProblemReport(
            userId = "123", // Replace with actual user ID if available
            description = description,
            timestamp = System.currentTimeMillis()
        )

        apiService.reportProblem(problemReport).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@ReportProblemActivity, "Problem reported successfully", Toast.LENGTH_SHORT).show()
                    problemDescriptionInput.text.clear() // Clear the input field after success
                } else {
                    Toast.makeText(this@ReportProblemActivity, "Failed to report problem", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@ReportProblemActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
