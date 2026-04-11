package com.womanglobal.connecther

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.womanglobal.connecther.adapters.SearchAdapter
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CategoryUsersActivity : AppCompatActivity() {

    private lateinit var searchAdapter: SearchAdapter
    private val users = mutableListOf<User>()
    private lateinit var categoryName: String
    private lateinit var serviceId: String
    private lateinit var noProvidersLayout: LinearLayout
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var nearbyHint: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val requestFineLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        fetchUsersForCategory(serviceId)
    }

    private val usersRefreshRunnable = object : Runnable {
        override fun run() {
            fetchUsersForCategory(serviceId)
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_users)

        categoryName = intent.getStringExtra("categoryName") ?: ""
        serviceId = intent.getStringExtra("service_id") ?: ""

        noProvidersLayout = findViewById(R.id.noProvidersLayout)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
        nearbyHint = findViewById(R.id.nearbyHint)

        val categoryTitle = findViewById<TextView>(R.id.categoryTitle)
        categoryTitle.text = categoryName

        findViewById<AppCompatImageButton>(R.id.buttonCategoryBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        searchAdapter = SearchAdapter(users, categoryName, serviceId)

        usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CategoryUsersActivity)
            adapter = searchAdapter
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            nearbyHint.text = getString(R.string.providers_all_hint)
        } else {
            nearbyHint.text = getString(R.string.providers_nearby_hint)
        }

        startUsersRefreshing()
    }

    private fun fetchUsersForCategory(serviceId: String) {
        lifecycleScope.launch {
            val hasLoc = ContextCompat.checkSelfPermission(
                this@CategoryUsersActivity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            nearbyHint.text = if (hasLoc) {
                getString(R.string.providers_nearby_hint)
            } else {
                getString(R.string.providers_all_hint)
            }

            val latLng = if (hasLoc) lastLatLngOrNull() else null
            val providerRows = runCatching {
                SupabaseData.getProvidersForService(
                    serviceId,
                    seekerLat = latLng?.first,
                    seekerLng = latLng?.second,
                )
            }.getOrDefault(emptyList())
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

    @SuppressLint("MissingPermission")
    private suspend fun lastLatLngOrNull(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return suspendCoroutine { cont ->
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc?.let { it.latitude to it.longitude }) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun startUsersRefreshing() {
        handler.post(usersRefreshRunnable)
    }

    private fun stopUsersRefreshing() {
        handler.removeCallbacks(usersRefreshRunnable)
    }

    override fun onResume() {
        super.onResume()
        fetchUsersForCategory(serviceId)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUsersRefreshing()
    }
}
