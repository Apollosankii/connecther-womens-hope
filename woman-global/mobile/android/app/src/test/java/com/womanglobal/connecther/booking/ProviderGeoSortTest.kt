package com.womanglobal.connecther.booking

import com.womanglobal.connecther.data.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGeoSortTest {

    private fun user(
        id: String,
        userName: String,
        lat: Double?,
        lng: Double?,
    ): User = User(
        id = id,
        first_name = "",
        last_name = "",
        title = null,
        user_name = userName,
        nat_id = null,
        dob = null,
        gender = null,
        occupation = null,
        pic = null,
        isIdVerified = null,
        isMobileVerified = null,
        isAvailable = true,
        details = null,
        phoneNumber = null,
        country = null,
        county = null,
        area_name = null,
        email = null,
        userDbId = null,
        isServiceProvider = null,
        isProviderApplicationPending = null,
        workingHours = null,
        availableForBooking = null,
        professionalTitle = null,
        latitude = lat,
        longitude = lng,
    )

    @Test
    fun haversineMeters_nairobiPoints_smallDistance() {
        // ~1 km apart
        val aLat = -1.2921
        val aLng = 36.8219
        val bLat = -1.3011
        val bLng = 36.8219
        val m = ProviderGeoSort.haversineMeters(aLat, aLng, bLat, bLng)
        assertTrue(m in 900.0..1200.0)
    }

    @Test
    fun sortByDistance_ordersClosestFirst_appendsNoCoords() {
        val seekerLat = 0.0
        val seekerLng = 0.0
        val far = user("1", "far", 1.0, 1.0)
        val near = user("2", "near", 0.01, 0.01)
        val noCoords = user("3", "anon", null, null)
        val input = listOf(far, near, noCoords)
        val sorted = ProviderGeoSort.sortByDistance(input, seekerLat, seekerLng)
        assertEquals(listOf(near, far, noCoords), sorted)
    }

    @Test
    fun sortByDistance_withoutSeeker_returnsOriginalOrder() {
        val a = user("a", "a", 1.0, 1.0)
        val b = user("b", "b", 2.0, 2.0)
        val list = listOf(a, b)
        assertEquals(list, ProviderGeoSort.sortByDistance(list, null, 0.0))
        assertEquals(list, ProviderGeoSort.sortByDistance(list, 0.0, null))
    }

    @Test
    fun providerRef_prefersUserName() {
        val u = user("id-1", "refname", null, null)
        assertEquals("refname", ProviderGeoSort.providerRef(u))
    }

    @Test
    fun providerRef_fallsBackToId() {
        val u = user("id-2", "  ", null, null)
        assertEquals("id-2", ProviderGeoSort.providerRef(u))
    }
}
