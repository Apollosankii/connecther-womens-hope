package com.womanglobal.connecther

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.adapters.SearchAdapter
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class CategoryUsersActivity : AppCompatActivity() {

    private lateinit var searchAdapter: SearchAdapter
    private val users = mutableListOf<User>()
    private lateinit var categoryName: String
    private lateinit var serviceId: String
    private lateinit var noProvidersLayout: LinearLayout
    private lateinit var usersRecyclerView: RecyclerView
    private val handler = Handler(Looper.getMainLooper())

    private val usersRefreshRunnable = object : Runnable {
        override fun run() {
            fetchUsersForCategory(serviceId)
            handler.postDelayed(this, 5000) // Refresh every 5 seconds
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_users)

        // Retrieve category name and service ID from intent
        categoryName = intent.getStringExtra("categoryName") ?: ""
        serviceId = intent.getStringExtra("service_id") ?: ""

        noProvidersLayout = findViewById(R.id.noProvidersLayout)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
//        val recyclerView = findViewById<RecyclerView>(R.id.usersRecyclerView)
        val categoryTitle = findViewById<TextView>(R.id.categoryTitle)
        categoryTitle.text = categoryName

        // Initialize the adapter with serviceId
        searchAdapter = SearchAdapter(users, categoryName, serviceId)

        usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CategoryUsersActivity)
            adapter = searchAdapter
        }

        // Fetch users for the selected category
        startUsersRefreshing()
    }

    private fun fetchUsersForCategory(serviceId: String) {
        lifecycleScope.launch {
            val providerRows = runCatching { SupabaseData.getProvidersForService(serviceId) }.getOrDefault(emptyList())
            users.clear()
            users.addAll(providerRows)
            searchAdapter.notifyDataSetChanged()
            if (users.isEmpty()) {
                noProvidersLayout.visibility = View.VISIBLE
                usersRecyclerView.visibility = View.GONE
            } else {
                noProvidersLayout.visibility = View.GONE
                usersRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun startUsersRefreshing() {
        handler.post(usersRefreshRunnable)
    }

    private fun stopUsersRefreshing() {
        handler.removeCallbacks(usersRefreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUsersRefreshing()
    }
}


