package com.womanglobal.connecther

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.womanglobal.connecther.booking.ProviderGeoSort
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Auto-match: shows one provider at a time (closest first when GPS + live locations available).
 * Primary action opens [RequestBookingActivity] (checkout). "Book another provider" skips the
 * current suggestion. "View provider profile" opens [ProfileActivity] in recommendation mode.
 */
class ProviderRecommendationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
        const val EXTRA_SERVICE_NAME = "service_name"
    }

    private val requestBookingLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                finish()
            }
        }

    private val suggestedProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> finish()
                ProfileActivity.RESULT_BOOK_ANOTHER_PROVIDER -> {
                    val u = currentCandidate() ?: return@registerForActivityResult
                    rejectedRefs.add(ProviderGeoSort.providerRef(u))
                    if (currentCandidate() == null) {
                        Toast.makeText(this, R.string.recommendation_no_more_providers, Toast.LENGTH_LONG).show()
                        showEmptyOrCard()
                    } else {
                        bindCurrentCandidate()
                    }
                }
                else -> { }
            }
        }

    private lateinit var serviceIdStr: String
    private var serviceName: String = ""

    private lateinit var progress: ProgressBar
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var layoutProviderActions: MaterialCardView
    private lateinit var cardProvider: com.google.android.material.card.MaterialCardView
    private lateinit var textEmptyMessage: TextView
    private lateinit var buttonBrowseAll: MaterialButton
    private lateinit var imageProfile: ImageView
    private lateinit var textProviderName: TextView
    private lateinit var textProviderTitle: TextView
    private lateinit var textProviderArea: TextView
    private lateinit var textDistance: TextView
    private lateinit var buttonAccept: AppCompatButton
    private lateinit var buttonReject: MaterialButton
    private lateinit var buttonViewProfile: MaterialButton

    private val rejectedRefs = mutableSetOf<String>()
    private var sortedProviders: List<User> = emptyList()
    private var seekerLat: Double? = null
    private var seekerLng: Double? = null

    private var prefillPrice: String? = null
    private var quoteLinesJson: String? = null

    private val requestFineLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            loadProviders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_recommendation)

        serviceIdStr = intent.getStringExtra(EXTRA_SERVICE_ID).orEmpty()
        serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME).orEmpty()
        prefillPrice = intent.getStringExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE)
        quoteLinesJson = intent.getStringExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON)

        if (serviceIdStr.isBlank()) {
            Toast.makeText(this, R.string.service_menu_missing_params, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.buttonBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.textSubtitle).text =
            getString(R.string.recommendation_subtitle_with_service, serviceName.ifBlank { getString(R.string.service_menu_title) })

        progress = findViewById(R.id.progressRecommendation)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        layoutProviderActions = findViewById(R.id.layoutProviderActions)
        cardProvider = findViewById(R.id.cardProvider)
        textEmptyMessage = findViewById(R.id.textEmptyMessage)
        buttonBrowseAll = findViewById(R.id.buttonBrowseAll)
        imageProfile = findViewById(R.id.imageProfile)
        textProviderName = findViewById(R.id.textProviderName)
        textProviderTitle = findViewById(R.id.textProviderTitle)
        textProviderArea = findViewById(R.id.textProviderArea)
        textDistance = findViewById(R.id.textDistance)
        buttonAccept = findViewById(R.id.buttonAccept)
        buttonReject = findViewById(R.id.buttonReject)
        buttonViewProfile = findViewById(R.id.buttonViewProfile)

        buttonBrowseAll.setOnClickListener {
            startActivity(
                Intent(this, CategoryUsersActivity::class.java).apply {
                    putExtra("categoryName", serviceName)
                    putExtra("service_id", serviceIdStr)
                },
            )
        }

        buttonAccept.setOnClickListener {
            val u = currentCandidate() ?: return@setOnClickListener
            val ref = ProviderGeoSort.providerRef(u)
            if (ref.isBlank() || serviceIdStr.isBlank()) {
                Toast.makeText(this, R.string.service_menu_missing_params, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val displayName = "${u.first_name} ${u.last_name}".trim().ifBlank { u.user_name.ifBlank { u.id } }
            requestBookingLauncher.launch(
                Intent(this, RequestBookingActivity::class.java).apply {
                    putExtra("provider_ref", ref)
                    putExtra("service_id", serviceIdStr)
                    putExtra("provider_name", displayName)
                    putExtra("provider_pic", u.pic.orEmpty())
                    putExtra("service_name", serviceName)
                    putExtra(RequestBookingActivity.EXTRA_NOTIFY_PARENT_ON_BOOKING_SUCCESS, true)
                    prefillPrice?.takeIf { it.isNotBlank() }?.let {
                        putExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE, it)
                    }
                    quoteLinesJson?.takeIf { it.isNotBlank() }?.let {
                        putExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON, it)
                    }
                },
            )
        }

        buttonViewProfile.setOnClickListener {
            val u = currentCandidate() ?: return@setOnClickListener
            suggestedProfileLauncher.launch(
                Intent(this, ProfileActivity::class.java).apply {
                    putExtra("user", u)
                    putExtra("service_id", serviceIdStr)
                    putExtra("service_name", serviceName)
                    putExtra(ProfileActivity.EXTRA_FROM_PROVIDER_RECOMMENDATION, true)
                    prefillPrice?.takeIf { it.isNotBlank() }?.let {
                        putExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE, it)
                    }
                    quoteLinesJson?.takeIf { it.isNotBlank() }?.let {
                        putExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON, it)
                    }
                },
            )
        }

        buttonReject.setOnClickListener {
            val u = currentCandidate() ?: return@setOnClickListener
            rejectedRefs.add(ProviderGeoSort.providerRef(u))
            if (currentCandidate() == null) {
                Toast.makeText(this, R.string.recommendation_no_more_providers, Toast.LENGTH_LONG).show()
                showEmptyOrCard()
            } else {
                bindCurrentCandidate()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            loadProviders()
        }
    }

    private fun loadProviders() {
        progress.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE
        cardProvider.visibility = View.GONE
        layoutProviderActions.visibility = View.GONE
        lifecycleScope.launch {
            val latLng = if (
                ContextCompat.checkSelfPermission(
                    this@ProviderRecommendationActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                lastLatLngOrNull()
            } else {
                null
            }
            seekerLat = latLng?.first
            seekerLng = latLng?.second

            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    SupabaseData.getProvidersForService(
                        serviceIdStr,
                        seekerLat = seekerLat,
                        seekerLng = seekerLng,
                    )
                }.getOrDefault(emptyList())
            }
            sortedProviders = ProviderGeoSort.sortByDistance(raw, seekerLat, seekerLng)
            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                rejectedRefs.clear()
                showEmptyOrCard()
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

    private fun currentCandidate(): User? =
        sortedProviders.firstOrNull { !rejectedRefs.contains(ProviderGeoSort.providerRef(it)) }

    private fun showEmptyOrCard() {
        val next = currentCandidate()
        if (next == null) {
            layoutEmpty.visibility = View.VISIBLE
            cardProvider.visibility = View.GONE
            layoutProviderActions.visibility = View.GONE
            val msg = if (sortedProviders.isNotEmpty() && rejectedRefs.size >= sortedProviders.size) {
                getString(R.string.recommendation_exhausted)
            } else {
                getString(R.string.recommendation_no_providers)
            }
            textEmptyMessage.text = msg
        } else {
            layoutEmpty.visibility = View.GONE
            cardProvider.visibility = View.VISIBLE
            layoutProviderActions.visibility = View.VISIBLE
            bindCurrentCandidate()
        }
    }

    private fun bindCurrentCandidate() {
        val u = currentCandidate() ?: run {
            layoutEmpty.visibility = View.VISIBLE
            cardProvider.visibility = View.GONE
            layoutProviderActions.visibility = View.GONE
            return
        }
        val name = "${u.first_name} ${u.last_name}".trim().ifBlank { u.user_name.ifBlank { u.id } }
        textProviderName.text = name
        textProviderTitle.text = u.title?.takeIf { it.isNotBlank() } ?: u.professionalTitle.orEmpty()
        textProviderTitle.visibility = if (textProviderTitle.text.isNullOrBlank()) View.GONE else View.VISIBLE
        val area = listOfNotNull(u.area_name, u.county, u.country).firstOrNull { it.isNotBlank() }
        if (area.isNullOrBlank()) {
            textProviderArea.visibility = View.GONE
        } else {
            textProviderArea.visibility = View.VISIBLE
            textProviderArea.text = area
        }
        Glide.with(this)
            .load(u.pic)
            .placeholder(R.drawable.ic_avatar_neutral)
            .circleCrop()
            .into(imageProfile)

        val plat = u.latitude
        val plng = u.longitude
        val slat = seekerLat
        val slng = seekerLng
        if (plat != null && plng != null && slat != null && slng != null) {
            val m = ProviderGeoSort.haversineMeters(slat, slng, plat, plng)
            val km = m / 1000.0
            textDistance.visibility = View.VISIBLE
            textDistance.text = getString(R.string.recommendation_distance_km, km)
        } else {
            textDistance.visibility = View.GONE
        }
    }
}
