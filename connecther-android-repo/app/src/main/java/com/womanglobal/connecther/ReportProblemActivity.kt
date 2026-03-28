package com.womanglobal.connecther

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.womanglobal.connecther.auth.AuthPreferences
import com.womanglobal.connecther.data.ProblemReport
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReportProblemActivity : AppCompatActivity() {

    private lateinit var problemDescriptionInput: EditText
    private lateinit var sendReportButton: Button
    private val apiService = ApiServiceFactory.createApiService()

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
        val userId = AuthPreferences.getFirebaseUid(this)
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: "unknown"

        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                val ok = SupabaseData.reportProblem(description)
                if (ok) {
                    Toast.makeText(this@ReportProblemActivity, "Problem reported successfully", Toast.LENGTH_SHORT).show()
                    problemDescriptionInput.text.clear()
                } else {
                    Toast.makeText(this@ReportProblemActivity, "Failed to report problem", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val problemReport = ProblemReport(userId = userId, description = description, timestamp = System.currentTimeMillis())
            apiService.reportProblem(problemReport).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ReportProblemActivity, "Problem reported successfully", Toast.LENGTH_SHORT).show()
                        problemDescriptionInput.text.clear()
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
}
