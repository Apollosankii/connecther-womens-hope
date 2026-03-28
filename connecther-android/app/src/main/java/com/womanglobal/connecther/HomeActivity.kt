package com.womanglobal.connecther

import ApiServiceFactory
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ActivityCompat
import androidx.core.view.forEachIndexed
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.womanglobal.connecther.databinding.ActivityHomeBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.LocationRequestBody
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.ui.adapter.ViewPagerAdapter
import com.womanglobal.connecther.utils.UIHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var bottomNavigationView: BottomNavigationView
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        // Check if user is logged in
        if (!sharedPreferences.getBoolean("isLoggedIn", false)) {
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, com.womanglobal.connecther.auth.AuthGateActivity::class.java))
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val viewPager = binding.viewPager
        bottomNavigationView = binding.bottomNavigation


       // Initialize ViewPager with the appropriate adapter
        val viewPagerAdapter = ViewPagerAdapter(this, sharedPreferences)
        viewPager.adapter = viewPagerAdapter


        // Get the fragment to open from the intent
        val fragmentToOpen = intent.getStringExtra("fragment_to_open")

        // Set the appropriate ViewPager page based on the notification
        when (fragmentToOpen) {
            "messages" -> viewPager.currentItem = 2
            "jobs" -> viewPager.currentItem = 3
            "profile" -> viewPager.currentItem = 4
            else -> viewPager.currentItem = 0
        }

        // Update bottom navigation menu based on user type
        setupBottomNavigation()

        // Register FCM token with Supabase when using Clerk/Supabase path
        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    val deviceId = "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.ID}"
                    SupabaseData.upsertFcmToken(token, deviceId)
                }
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNavigationView.menu.getItem(position).isChecked = true
            }
        })

        bottomNavigationView.setOnItemSelectedListener { item ->
            val index = menuItemToIndexMap[item.itemId] ?: return@setOnItemSelectedListener false
            viewPager.currentItem = index
            true
        }


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true) // Minimize app instead of exiting
            }
        })

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up location request
        locationRequest = LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setIntervalMillis(LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .build()


        // Define the callback for location updates
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
//                    UIHelper.showAlertDialog(this@HomeActivity, "New Location!", "${location.latitude}, ${location.longitude}")
                    sendLocationToServer(location.latitude, location.longitude)
                }
            }
        }

        // Request location updates immediately
        checkAndRequestPermissions()

        // Schedule periodic location updates every 5 minutes
        startLocationUpdates()
    }

    /**
     * Dynamically update bottom navigation menu based on user type
     */
    private val menuItemToIndexMap = mutableMapOf<Int, Int>()

    private fun setupBottomNavigation() {
        val isProvider = sharedPreferences.getBoolean("isProvider", false)

        bottomNavigationView.menu.clear()
        bottomNavigationView.inflateMenu(
            if (isProvider) R.menu.bottom_nav_provider_menu
            else R.menu.bottom_nav_menu
        )

        // Reset mapping
        menuItemToIndexMap.clear()

        // Populate the mapping dynamically based on menu order
        bottomNavigationView.menu.forEachIndexed { index, item ->
            menuItemToIndexMap[item.itemId] = index
        }
    }


    /**
     * Check permissions and request location
     */
    private fun checkAndRequestPermissions() {
        when {
            hasLocationPermissions() -> {
                requestLocation()
            }
            else -> {
                requestPermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    /**
     * Check if the app has location permissions.
     */
    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests location permission using Android's Activity Result API.
     */
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                requestLocation()
            } else {
                UIHelper.showToastShort(this, "Location permission is required for this feature.")
                openLocationSettings()
            }
        }


    /**
     * Opens location settings in case permissions are denied.
     */
    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    /**
     * Request the user's current location.
     */
    private fun requestLocation() {
        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled) {
            UIHelper.showToastShort(this, "Please enable GPS for accurate location.")
            openLocationSettings()
            return
        }


        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
//                UIHelper.showAlertDialog(this, "Last Known Location", "${location.latitude}, ${location.longitude}")
                sendLocationToServer(location.latitude, location.longitude)
            } else {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            }
        }.addOnFailureListener {
            UIHelper.showToastShort(this, "Failed to get location.")
        }
    }

    /**
     * Send the location to the server.
     */
    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        if (com.womanglobal.connecther.supabase.SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                val ok = com.womanglobal.connecther.supabase.SupabaseData.upsertLiveLocation(latitude, longitude)
                val msg = if (ok) "Location updated" else "Failed to update location"
                Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            val requestBody = LocationRequestBody(latitude, longitude)
            apiService.sendLocationUpdate(requestBody).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@HomeActivity, "Location updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HomeActivity, "Failed to update location", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Toast.makeText(this@HomeActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    /**
     * Start automatic location updates.
     */
    private fun startLocationUpdates() {
        if (!hasLocationPermissions()) return

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        handler.postDelayed(object : Runnable {
            override fun run() {
                requestLocation()
                handler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
            }
        }, LOCATION_UPDATE_INTERVAL)
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                requestLocation()
            } else {
                UIHelper.showToastShort(this, "Location permission is required.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Stop updates when activity is destroyed
        fusedLocationClient.removeLocationUpdates(locationCallback) // Stop location updates
    }

    companion object {
        private const val LOCATION_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes in milliseconds
        private const val LOCATION_FASTEST_INTERVAL = 2 * 60 * 1000L // 2 minutes in milliseconds
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

    }
}
