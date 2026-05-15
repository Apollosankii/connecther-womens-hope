package com.womanglobal.connecther.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingQuoteAggregatorTest {

    @Test
    fun total_sumsLines() {
        val lines = listOf(
            BookingQuoteAggregator.Line("Wash", 200.0, 2),
            BookingQuoteAggregator.Line("Iron", 50.0, 1),
        )
        assertEquals(450.0, BookingQuoteAggregator.total(lines), 0.001)
    }

    @Test
    fun appendQuoteBreakdown_emptyLines_returnsTrimmedMessageOrNull() {
        assertNull(BookingQuoteAggregator.appendQuoteBreakdown("  ", emptyList()))
        assertEquals("Hi", BookingQuoteAggregator.appendQuoteBreakdown(" Hi ", emptyList()))
    }

    @Test
    fun appendQuoteBreakdown_withLines_appendsQuoteBlock() {
        val lines = listOf(BookingQuoteAggregator.Line("Wash", 200.0, 2))
        val out = BookingQuoteAggregator.appendQuoteBreakdown("Please book", lines)!!
        assertEquals(true, out.contains("Please book"))
        assertEquals(true, out.contains("Quote:"))
        assertEquals(true, out.contains("Wash"))
    }
}
