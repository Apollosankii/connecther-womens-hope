package com.womanglobal.connecther.data

data class Service(
    val service_id: String,
    val name: String,
    val pic: String,
    val description: String = "",
    val min_price: Double? = null,
    val isFullSpan: Boolean = false,
    var fallbackImageResId: Int? = null
)
