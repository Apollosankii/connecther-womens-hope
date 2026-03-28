package com.womanglobal.connecther.data

data class Service(
    val service_id: String,
    val name: String,
    val pic: String,
    val description: String? = null,
    val min_price: Double? = null,
    val isFullSpan: Boolean = false, // Optional, use this to determine if it should span full width (like GBV Hotline)
    var fallbackImageResId: Int? = null // Optional fallback image resource ID
)
