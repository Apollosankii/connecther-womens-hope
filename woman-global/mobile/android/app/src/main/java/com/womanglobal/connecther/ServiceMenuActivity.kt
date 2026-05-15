package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.card.MaterialCardView
import com.womanglobal.connecther.booking.BookingQuoteAggregator
import com.womanglobal.connecther.booking.BookingQuoteIntentHelper
import com.womanglobal.connecther.booking.ServiceMenuRow
import com.womanglobal.connecther.booking.ServiceTaskMenuParser
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Task menu (quantities / toggles) for a service before booking.
 * With `provider_ref` (profile flow): if `task_menu` is empty, opens [RequestBookingActivity] directly.
 * Without `provider_ref` (service grid): user stays here until they tap the bottom button —
 * **Book now** after choosing line items, or **Find a provider** when no line-item menu exists.
 */
class ServiceMenuActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var textTotal: TextView
    private lateinit var textMenuPlaceholder: TextView
    private lateinit var buttonBook: AppCompatButton
    private lateinit var progress: ProgressBar
    private lateinit var imageBanner: ImageView
    private lateinit var cardServiceBanner: MaterialCardView

    private val menuRows = mutableListOf<ServiceMenuRow>()
    private var serviceDisplayName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_menu)

        val providerRef = intent.getStringExtra("provider_ref")?.trim()?.takeIf { it.isNotBlank() }
        val serviceIdStr = intent.getStringExtra("service_id").orEmpty()
        val serviceId = serviceIdStr.toIntOrNull()
        serviceDisplayName = intent.getStringExtra("service_name").orEmpty()

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.textToolbarTitle).text =
            serviceDisplayName.ifBlank { getString(R.string.service_menu_title) }
        val textBannerOverlay = findViewById<TextView>(R.id.textBannerOverlay)
        textBannerOverlay.text =
            getString(R.string.service_menu_banner_overlay, serviceDisplayName.ifBlank { getString(R.string.service_menu_title) })

        recycler = findViewById(R.id.recyclerMenu)
        textTotal = findViewById(R.id.textTotal)
        textMenuPlaceholder = findViewById(R.id.textMenuPlaceholder)
        buttonBook = findViewById(R.id.buttonBookNow)
        progress = findViewById(R.id.progressMenu)
        imageBanner = findViewById(R.id.imageBanner)
        cardServiceBanner = findViewById(R.id.cardServiceBanner)

        recycler.layoutManager = LinearLayoutManager(this)

        if (serviceId == null) {
            Toast.makeText(this, R.string.service_menu_missing_params, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        buttonBook.setOnClickListener { onBookNowClicked(providerRef, serviceIdStr) }

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val svc = withContext(Dispatchers.IO) { SupabaseData.getServiceById(serviceId) }
            val parsed = svc?.let { ServiceTaskMenuParser.parse(it.task_menu_json) }
            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                if (parsed == null) {
                    if (providerRef != null) {
                        forwardAfterTaskMenu(providerRef, serviceIdStr, null, null)
                        finish()
                        return@withContext
                    }
                    if (svc == null) {
                        Toast.makeText(
                            this@ServiceMenuActivity,
                            R.string.service_menu_missing_params,
                            Toast.LENGTH_LONG,
                        ).show()
                        finish()
                        return@withContext
                    }
                    showNoLineItemMenuFromServiceGrid(svc, serviceIdStr)
                    return@withContext
                }
                textMenuPlaceholder.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                textTotal.visibility = View.VISIBLE
                buttonBook.setText(R.string.service_menu_book_now)
                buttonBook.setOnClickListener { onBookNowClicked(providerRef, serviceIdStr) }
                val service = requireNotNull(svc) { "service row required when task menu is present" }
                bindBanner(parsed.bannerImageUrl, service.pic)
                menuRows.clear()
                menuRows.addAll(parsed.rows)
                recycler.adapter = ServiceMenuAdapter(menuRows) { refreshTotal() }
                refreshTotal()
            }
        }
    }

    /** Service grid only: do not auto-open [ProviderRecommendationActivity] when there is no task menu. */
    private fun showNoLineItemMenuFromServiceGrid(svc: Service, serviceIdStr: String) {
        textMenuPlaceholder.visibility = View.VISIBLE
        textMenuPlaceholder.setText(R.string.service_menu_not_configured)
        recycler.visibility = View.GONE
        textTotal.visibility = View.GONE
        bindBanner(null, svc.pic)
        buttonBook.setText(R.string.service_menu_find_provider)
        buttonBook.setOnClickListener {
            openProviderRecommendation(serviceIdStr, null, null)
            finish()
        }
    }

    private fun bindBanner(bannerUrl: String?, servicePic: String?) {
        val url = bannerUrl?.takeIf { it.isNotBlank() } ?: servicePic?.takeIf { it.isNotBlank() }
        val overlay = findViewById<TextView>(R.id.textBannerOverlay)
        if (url == null) {
            cardServiceBanner.visibility = View.GONE
            overlay.visibility = View.GONE
        } else {
            cardServiceBanner.visibility = View.VISIBLE
            overlay.visibility = View.VISIBLE
            Glide.with(this)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.connecther_)
                .error(R.drawable.connecther_)
                .into(imageBanner)
        }
    }

    private fun refreshTotal() {
        val lines = ServiceTaskMenuParser.buildQuoteLines(menuRows)
        val total = BookingQuoteAggregator.total(lines)
        textTotal.text = getString(R.string.service_menu_total_kes, total)
    }

    private fun onBookNowClicked(providerRef: String?, serviceIdStr: String) {
        val lines = ServiceTaskMenuParser.buildQuoteLines(menuRows)
        val total = BookingQuoteAggregator.total(lines)
        if (total <= 0.0) {
            Toast.makeText(this, R.string.service_menu_pick_items, Toast.LENGTH_LONG).show()
            return
        }
        val json = BookingQuoteIntentHelper.encodeLines(lines)
        forwardAfterTaskMenu(providerRef, serviceIdStr, total, json)
        finish()
    }

    /**
     * From profile flow [providerRef] is set → book that provider directly.
     * From service grid [providerRef] is null → auto-match in [ProviderRecommendationActivity].
     */
    private fun forwardAfterTaskMenu(
        providerRef: String?,
        serviceIdStr: String,
        prefillTotal: Double?,
        quoteLinesJson: String?,
    ) {
        if (providerRef != null) {
            openRequestBooking(providerRef, serviceIdStr, prefillTotal, quoteLinesJson)
        } else {
            openProviderRecommendation(serviceIdStr, prefillTotal, quoteLinesJson)
        }
    }

    private fun openProviderRecommendation(
        serviceIdStr: String,
        prefillTotal: Double?,
        quoteLinesJson: String?,
    ) {
        startActivity(
            Intent(this, ProviderRecommendationActivity::class.java).apply {
                putExtra(ProviderRecommendationActivity.EXTRA_SERVICE_ID, serviceIdStr)
                putExtra(ProviderRecommendationActivity.EXTRA_SERVICE_NAME, serviceDisplayName)
                if (prefillTotal != null && prefillTotal > 0) {
                    putExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE, prefillTotal.toString())
                }
                if (!quoteLinesJson.isNullOrBlank()) {
                    putExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON, quoteLinesJson)
                }
            },
        )
    }

    private fun openRequestBooking(
        providerRef: String,
        serviceIdStr: String,
        prefillTotal: Double?,
        quoteLinesJson: String?,
    ) {
        startActivity(
            Intent(this, RequestBookingActivity::class.java).apply {
                putExtra("provider_ref", providerRef)
                putExtra("service_id", serviceIdStr)
                putExtra("service_name", serviceDisplayName)
                intent.getStringExtra("provider_name")?.takeIf { it.isNotBlank() }?.let {
                    putExtra("provider_name", it)
                }
                if (prefillTotal != null && prefillTotal > 0) {
                    putExtra(RequestBookingActivity.EXTRA_PREFILL_PRICE, prefillTotal.toString())
                }
                if (!quoteLinesJson.isNullOrBlank()) {
                    putExtra(RequestBookingActivity.EXTRA_QUOTE_LINES_JSON, quoteLinesJson)
                }
            },
        )
    }
}
