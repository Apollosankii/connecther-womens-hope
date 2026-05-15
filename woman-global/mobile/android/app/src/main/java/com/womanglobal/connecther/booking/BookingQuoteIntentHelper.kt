package com.womanglobal.connecther.booking

import org.json.JSONArray
import org.json.JSONObject

/** Serialize [BookingQuoteAggregator.Line] for [RequestBookingActivity] intent extras. */
object BookingQuoteIntentHelper {

    fun encodeLines(lines: List<BookingQuoteAggregator.Line>): String {
        val a = JSONArray()
        for (l in lines) {
            a.put(
                JSONObject()
                    .put("label", l.label)
                    .put("unitPrice", l.unitPrice)
                    .put("quantity", l.quantity),
            )
        }
        return a.toString()
    }

    fun decodeLines(json: String?): List<BookingQuoteAggregator.Line> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(json)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val label = o.optString("label").trim()
                    if (label.isEmpty()) continue
                    val qty = o.optDouble("quantity", 0.0).toInt().coerceAtLeast(0)
                    add(
                        BookingQuoteAggregator.Line(
                            label = label,
                            unitPrice = o.optDouble("unitPrice", 0.0).coerceAtLeast(0.0),
                            quantity = qty,
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }
}
