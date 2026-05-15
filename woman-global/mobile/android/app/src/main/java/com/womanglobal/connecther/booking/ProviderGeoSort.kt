package com.womanglobal.connecther.booking

import com.womanglobal.connecther.data.User
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sorts provider [User] rows by great-circle distance to the seeker when both sides have coordinates.
 * Providers without coordinates are appended after sorted ones (stable relative order).
 */
object ProviderGeoSort {

    /** Earth mean radius in meters (WGS84 approximation). */
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * @param seekerLat seeker latitude, or null to skip distance sort
     * @param seekerLng seeker longitude, or null to skip distance sort
     */
    fun sortByDistance(providers: List<User>, seekerLat: Double?, seekerLng: Double?): List<User> {
        if (providers.isEmpty()) return providers
        if (seekerLat == null || seekerLng == null) return providers

        val withCoords = mutableListOf<Pair<User, Double>>()
        val withoutCoords = mutableListOf<User>()
        for (u in providers) {
            val plat = u.latitude
            val plng = u.longitude
            if (plat != null && plng != null) {
                val m = haversineMeters(seekerLat, seekerLng, plat, plng)
                withCoords.add(u to m)
            } else {
                withoutCoords.add(u)
            }
        }
        val sorted = withCoords.sortedBy { it.second }.map { it.first }
        return sorted + withoutCoords
    }

    fun providerRef(u: User): String = u.user_name.trim().ifBlank { u.id.trim() }
}
