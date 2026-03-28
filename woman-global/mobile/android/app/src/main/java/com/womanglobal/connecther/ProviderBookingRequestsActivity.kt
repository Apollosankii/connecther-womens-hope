package com.womanglobal.connecther

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ProviderBookingRequestsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_booking_requests)
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val recycler = findViewById<RecyclerView>(R.id.recyclerBookingRequests)
        val empty = findViewById<TextView>(R.id.emptyText)
        val progress = findViewById<View>(R.id.progressBar)
        recycler.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            val jobs = runCatching { SupabaseData.getPendingJobs() }.getOrDefault(emptyList())
            progress.visibility = View.GONE
            empty.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
            recycler.adapter = SimpleTextListAdapter(
                jobs.map { "#${it.job_id} ${it.service} - ${it.location}" },
                null
            )
        }
    }
}

