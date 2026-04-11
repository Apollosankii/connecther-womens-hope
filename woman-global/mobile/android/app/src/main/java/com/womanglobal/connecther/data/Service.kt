package com.womanglobal.connecther.data

data class Service(
    val service_id: String,
    val name: String,
    val pic: String,
    val description: String? = null,
    val min_price: Double? = null,
    /** Server-enforced discovery radius in meters when location is used (see services.search_radius_meters). */
    val search_radius_meters: Int = 10_000,
    val require_location_detail: Boolean = false,
    /** JSON array string of { "key", "label" } hints for booking UI; may be "[]". */
    val location_detail_schema_json: String = "[]",
    val isFullSpan: Boolean = false, // Optional, use this to determine if it should span full width (like GBV Hotline)
    var fallbackImageResId: Int? = null // Optional fallback image resource ID
)
