package com.womanglobal.connecther.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens **Google Maps** (native app when installed, otherwise a browser / default maps handler).
 * Uses official https://www.google.com/maps/search URLs so behavior matches the Google Maps app.
 */
object LocationMapUtils {

    private const val GMAPS_PACKAGE = "com.google.android.apps.maps"

    fun openInMaps(context: Context, location: String, lat: Double?, lng: Double?) {
        openGoogleMaps(context, location, lat, lng)
    }

    fun openGoogleMaps(context: Context, location: String, lat: Double?, lng: Double?) {
        val uri = buildGoogleMapsUri(location, lat, lng)
        if (uri == null) {
            Toast.makeText(context, "No location to show", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(GMAPS_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            intent.setPackage(null)
            runCatching { context.startActivity(intent) }
                .onFailure {
                    Toast.makeText(context, "Could not open Google Maps", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun buildGoogleMapsUri(location: String, lat: Double?, lng: Double?): Uri? {
        if (lat != null && lng != null) {
            return Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        }
        val t = location.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http", ignoreCase = true)) return Uri.parse(t)
        val coord = Regex("^-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?$").find(t)?.value
        if (coord != null) {
            val parts = coord.split(",")
            if (parts.size == 2) {
                val la = parts[0].toDoubleOrNull()
                val lo = parts[1].toDoubleOrNull()
                if (la != null && lo != null) {
                    return Uri.parse("https://www.google.com/maps/search/?api=1&query=$la,$lo")
                }
            }
        }
        return Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(t)}")
    }

    @Deprecated("Use buildGoogleMapsUri", ReplaceWith("buildGoogleMapsUri(location, lat, lng)"))
    fun buildUri(location: String, lat: Double?, lng: Double?): Uri? =
        buildGoogleMapsUri(location, lat, lng)
}
