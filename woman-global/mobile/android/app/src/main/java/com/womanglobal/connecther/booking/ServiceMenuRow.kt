package com.womanglobal.connecther.booking

/** One row in [ServiceTaskMenuParser.ParsedMenu.rows] for [ServiceMenuActivity]. */
sealed class ServiceMenuRow {
    data class Section(val title: String, val subtitle: String?) : ServiceMenuRow()

    data class QuantityLine(
        val key: String,
        val title: String,
        val unitLabel: String,
        val unitPrice: Double,
        val imageUrl: String?,
        var quantity: Int,
        val min: Int,
        val max: Int,
    ) : ServiceMenuRow()

    data class ToggleLine(
        val key: String,
        val title: String,
        val unitLabel: String,
        val unitPrice: Double,
        val imageUrl: String?,
        var checked: Boolean,
    ) : ServiceMenuRow()
}
