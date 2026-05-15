package com.womanglobal.connecther.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTaskMenuParserTest {

    @Test
    fun parse_sampleMenu_returnsRows() {
        val json =
            """{"rows":[{"type":"section","title":"Home Cleaning"},{"type":"quantity","key":"a","title":"Basic","unit_label":"bedrooms","unit_price":500,"min":0,"max":5,"default_qty":0},{"type":"toggle","key":"b","title":"Equipment","unit_label":"","unit_price":650,"default_checked":false}]}"""
        val parsed = ServiceTaskMenuParser.parse(json)!!
        assertEquals(3, parsed.rows.size)
        val qty = parsed.rows[1] as ServiceMenuRow.QuantityLine
        qty.quantity = 2
        val toggle = parsed.rows[2] as ServiceMenuRow.ToggleLine
        toggle.checked = true
        val lines = ServiceTaskMenuParser.buildQuoteLines(parsed.rows)
        assertEquals(2, lines.size)
        assertEquals(1650.0, BookingQuoteAggregator.total(lines), 0.01)
    }

    @Test
    fun intentHelper_roundTrip() {
        val original = listOf(
            BookingQuoteAggregator.Line("Wash", 200.0, 2),
            BookingQuoteAggregator.Line("Iron", 50.0, 1),
        )
        val enc = BookingQuoteIntentHelper.encodeLines(original)
        val dec = BookingQuoteIntentHelper.decodeLines(enc)
        assertEquals(original.size, dec.size)
        assertEquals("Wash", dec[0].label)
        assertEquals(200.0, dec[0].unitPrice, 0.001)
        assertEquals(2, dec[0].quantity)
    }

    @Test
    fun parse_empty_returnsNull() {
        assertEquals(null, ServiceTaskMenuParser.parse("{}"))
        assertEquals(null, ServiceTaskMenuParser.parse(null))
    }

    @Test
    fun parse_rootArray_wrapsRows() {
        val json = """[{"type":"quantity","key":"x","title":"One","unit_label":"u","unit_price":10,"min":0,"max":9,"default_qty":1}]"""
        val parsed = ServiceTaskMenuParser.parse(json)!!
        assertTrue(parsed.rows[0] is ServiceMenuRow.QuantityLine)
    }
}
