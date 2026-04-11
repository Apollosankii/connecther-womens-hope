package com.womanglobal.connecther

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEachIndexed
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.womanglobal.connecther.databinding.ActivityHomeBinding
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.ui.adapter.ViewPagerAdapter
import com.womanglobal.connecther.util.reduceDragSensitivity
import com.womanglobal.connecther.utils.UIHelper
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    /** Island height + bottom margin; applied to each tab’s scrollable, not ViewPager2 (so content can scroll into the gutters). */
    private var homeTabBottomInsetPx: Int = 0

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var bottomNavigationView: BottomNavigationView
    private val handler = Handler(Looper.getMainLooper())

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, R.string.notifications_permission_enabled, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        // Check if user is logged in
        if (!sharedPreferences.getBoolean("isLoggedIn", false)) {
            Toast.makeText(this, "Redirecting to login", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyHomeTransparentNavigationBar()
        wireViewPager2UnderFloatingIsland()
        registerHomeTabScrollInsetCallbacks()

        binding.bottomNavCardContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncFloatingIslandInsetsToTabs()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val mandatoryGestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            val leftInset = maxOf(statusBars.left, cutout.left, navBars.left, mandatoryGestures.left)
            val rightInset = maxOf(statusBars.right, cutout.right, navBars.right, mandatoryGestures.right)
            val bottomInset = maxOf(navBars.bottom, mandatoryGestures.bottom)
            val topInset = maxOf(statusBars.top, cutout.top)

            val baseH = resources.getDimensionPixelSize(R.dimen.bottom_nav_island_margin_horizontal)
            val baseB = resources.getDimensionPixelSize(R.dimen.bottom_nav_island_margin_bottom)
            (binding.bottomNavCardContainer.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = leftInset + baseH
                rightMargin = rightInset + baseH
                bottomMargin = bottomInset + baseB
            }.also { binding.bottomNavCardContainer.layoutParams = it }

            binding.bottomNavigation.setPadding(0, 0, 0, 0)
            binding.viewPager.updatePadding(
                left = leftInset,
                right = rightInset,
                top = topInset,
                bottom = 0,
            )

            binding.root.post { syncFloatingIslandInsetsToTabs() }

            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .build()
        }

        val viewPager = binding.viewPager
        bottomNavigationView = binding.bottomNavigation


       // Initialize ViewPager with the appropriate adapter
        val viewPagerAdapter = ViewPagerAdapter(this)
        viewPager.adapter = viewPagerAdapter
        viewPager.post {
            viewPager.reduceDragSensitivity()
            ensureInnerRecyclerViewClipToPaddingFalse()
        }

        applyFragmentFromIntent(intent)

        // Update bottom navigation menu based on user type
        setupBottomNavigation()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNavigationView.menu.getItem(position).isChecked = true
                if (homeTabBottomInsetPx > 0) {
                    supportFragmentManager.findFragmentByTag("f$position")
                        ?.let { applyIslandBottomInsetToTab(it) }
                }
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
                    sendLocationToServer(location.latitude, location.longitude)
                }
            }
        }

        // Request location updates immediately
        checkAndRequestPermissions()

        // Schedule periodic location updates every 5 minutes
        startLocationUpdates()

        handler.postDelayed({
            promptNotificationPermissionIfNeeded()
        }, 1800L)
    }

    private fun promptNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notification_permission_prompted", false)) return
        prefs.edit().putBoolean("notification_permission_prompted", true).apply()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.notifications_permission_title)
            .setMessage(R.string.notifications_permission_message)
            .setPositiveButton(R.string.notifications_permission_allow) { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.notifications_permission_not_now, null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::binding.isInitialized) {
            applyFragmentFromIntent(intent)
        }
    }

    /** Opens the tab matching FCM [fragment_to_open] (ViewPager: 0 Home … 4 Profile). */
    private fun applyFragmentFromIntent(intent: Intent?) {
        val fragmentToOpen = intent?.getStringExtra("fragment_to_open") ?: return
        val viewPager = binding.viewPager
        when (fragmentToOpen) {
            "services" -> viewPager.currentItem = 1
            "messages" -> viewPager.currentItem = 2
            "jobs" -> viewPager.currentItem = 3
            "profile" -> viewPager.currentItem = 4
            "home" -> viewPager.currentItem = 0
            else -> viewPager.currentItem = 0
        }
        bottomNavigationView.menu.getItem(viewPager.currentItem).isChecked = true
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
        lifecycleScope.launch {
            SupabaseData.upsertLiveLocation(latitude, longitude)
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

    /** Transparent gesture/nav region so only the island capsule reads as a solid bar. */
    private fun applyHomeTransparentNavigationBar() {
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val isNight =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightNavigationBars = !isNight
    }

    private fun registerHomeTabScrollInsetCallbacks() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?,
                ) {
                    if (!HOMETAB_FRAGMENT_TAG.matches(f.tag ?: "")) return
                    if (homeTabBottomInsetPx > 0) applyIslandBottomInsetToTab(f)
                }
            },
            false,
        )
    }

    /** Island size changed or system insets changed — push overlap onto each tab’s NestedScrollView / RecyclerView / ScrollView. */
    private fun syncFloatingIslandInsetsToTabs() {
        val card = binding.bottomNavCardContainer
        if (card.height <= 0) {
            card.post { syncFloatingIslandInsetsToTabs() }
            return
        }
        val lp = card.layoutParams as FrameLayout.LayoutParams
        homeTabBottomInsetPx = card.height + lp.bottomMargin
        applyIslandBottomInsetToAllTabs()
        ensureInnerRecyclerViewClipToPaddingFalse()
    }

    /** After async content shows a scrollable (e.g. Bookings list), re-apply nav island padding. */
    fun notifyTabScrollableContentChanged() {
        binding.bottomNavCardContainer.post { syncFloatingIslandInsetsToTabs() }
    }

    private fun applyIslandBottomInsetToAllTabs() {
        val overlap = homeTabBottomInsetPx
        if (overlap <= 0) return
        val n = binding.viewPager.adapter?.itemCount ?: return
        for (i in 0 until n) {
            supportFragmentManager.findFragmentByTag("f$i")?.let { applyIslandBottomInsetToTab(it) }
        }
    }

    private fun applyIslandBottomInsetToTab(f: Fragment) {
        val overlap = homeTabBottomInsetPx
        if (overlap <= 0) return
        val root = f.view ?: return
        val target = scrollableForBottomInset(root) ?: return
        applyIslandScrollPadding(target, overlap)
    }

    private fun scrollableForBottomInset(v: View): View? {
        when (v) {
            is NestedScrollView, is ScrollView ->
                if (v.isShown) return v else null
            is RecyclerView ->
                if (v.isShown) return v else null
            is ViewGroup -> {
                for (i in 0 until v.childCount) {
                    scrollableForBottomInset(v.getChildAt(i))?.let { return it }
                }
            }
        }
        return null
    }

    private fun applyIslandScrollPadding(target: View, overlapPx: Int) {
        if (!target.isAttachedToWindow) return
        val stored = target.getTag(R.id.home_scroll_base_padding_bottom)
        val base = when (stored) {
            is Int -> stored
            else -> {
                val b = target.paddingBottom
                target.setTag(R.id.home_scroll_base_padding_bottom, b)
                b
            }
        }
        target.updatePadding(bottom = base + overlapPx)
        when (target) {
            is NestedScrollView -> target.clipToPadding = false
            is ScrollView -> target.clipToPadding = false
            is RecyclerView -> {
                target.clipToPadding = false
                target.clipChildren = false
            }
        }
    }

    private fun wireViewPager2UnderFloatingIsland() {
        binding.viewPager.clipToPadding = false
        binding.viewPager.clipChildren = false
    }

    private fun viewPagerInnerRecyclerView(): RecyclerView? {
        for (i in 0 until binding.viewPager.childCount) {
            val child = binding.viewPager.getChildAt(i)
            if (child is RecyclerView) return child
        }
        return null
    }

    private fun ensureInnerRecyclerViewClipToPaddingFalse() {
        viewPagerInnerRecyclerView()?.apply {
            clipToPadding = false
            clipChildren = false
        }
    }

    companion object {
        private val HOMETAB_FRAGMENT_TAG = Regex("^f\\d+$")

        private const val LOCATION_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes in milliseconds
        private const val LOCATION_FASTEST_INTERVAL = 2 * 60 * 1000L // 2 minutes in milliseconds
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

    }
}
