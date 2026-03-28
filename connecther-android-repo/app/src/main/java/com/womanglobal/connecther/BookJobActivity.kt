package com.womanglobal.connecther


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.womanglobal.connecther.R

class BookJobActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_job)

        // Get the selected date from the intent
        val selectedDate = intent.getLongExtra("selectedDate", -1)

        // Implement search and booking functionality here
    }
}
