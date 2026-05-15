package com.womanglobal.connecther.booking

/**
 * Figma-style multi-line "menu" quotes collapsed into one [create_booking_request] price.
 * When [lines] is empty, [appendQuoteBreakdown] returns the user message unchanged (trimmed empty → null).
 */
object BookingQuoteAggregator {

    data class Line(val label: String, val unitPrice: Double, val quantity: Int) {
        init {
            require(label.isNotBlank()) { "label must not be blank" }
            require(unitPrice >= 0.0) { "unitPrice must be >= 0" }
            require(quantity >= 0) { "quantity must be >= 0" }
        }
    }

    fun total(lines: List<Line>): Double =
        lines.sumOf { it.unitPrice * it.quantity }

    fun breakdown(lines: List<Line>): String =
        lines.joinToString(separator = "\n") { line ->
            val sub = line.unitPrice * line.quantity
            "${line.label} × ${line.quantity} @ ${line.unitPrice} = $sub"
        }

    /**
     * @param userMessage optional note from the user (may be null/blank)
     * @param lines optional quote lines; empty → returns [userMessage] trimmed or null
     */
    fun appendQuoteBreakdown(userMessage: String?, lines: List<Line>): String? {
        val base = userMessage?.trim().orEmpty()
        if (lines.isEmpty()) return base.ifBlank { null }
        val quoteBlock = "Quote:\n${breakdown(lines)}"
        return when {
            base.isEmpty() -> quoteBlock
            else -> "$base\n\n$quoteBlock"
        }
    }
}
