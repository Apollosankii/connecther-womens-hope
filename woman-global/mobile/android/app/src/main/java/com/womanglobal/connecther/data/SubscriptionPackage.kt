package com.womanglobal.connecther.data

/**
 * Represents a subscription package offered to users.
 */
data class SubscriptionPackage(
    val id: String,
    val name: String,
    val description: String,
    val price: String,
    val duration: String, // e.g. "1 month", "3 months", "1 year"
    val features: List<String>,
    val isPopular: Boolean = false
)
