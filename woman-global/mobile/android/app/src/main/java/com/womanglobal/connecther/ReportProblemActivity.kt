package com.womanglobal.connecther

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ReportProblemActivity : AppCompatActivity() {

    private lateinit var problemDescriptionInput: EditText
    private lateinit var sendReportButton: Button

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
        sendReportButton.isEnabled = false
        lifecycleScope.launch {
            val ok = SupabaseData.reportProblem(description)
            sendReportButton.isEnabled = true
            if (ok) {
                Toast.makeText(this@ReportProblemActivity, "Problem reported successfully", Toast.LENGTH_SHORT).show()
                problemDescriptionInput.text.clear()
            } else {
                Toast.makeText(this@ReportProblemActivity, "Failed to report problem", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
