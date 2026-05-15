package com.womanglobal.connecther.booking

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses `services.task_menu` JSON (see Supabase migration `20260513180000_services_task_menu.sql`).
 */
object ServiceTaskMenuParser {

    data class ParsedMenu(
        val bannerImageUrl: String?,
        val rows: List<ServiceMenuRow>,
    )

    fun parse(json: String?): ParsedMenu? {
        val trimmed = json?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "null") return null
        return runCatching {
            when {
                trimmed.startsWith("[") ->
                    parseRoot(JSONObject().put("rows", JSONArray(trimmed)))
                else -> parseRoot(JSONObject(trimmed))
            }
        }.getOrNull()
    }

    private fun parseRoot(root: JSONObject): ParsedMenu? {
        val banner = root.optString("banner_image_url").takeIf { it.isNotBlank() }
        val arr = root.optJSONArray("rows") ?: return null
        val out = mutableListOf<ServiceMenuRow>()
        loop@ for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue@loop
            when (o.optString("type").trim().lowercase()) {
                "section" -> {
                    val title = o.optString("title").trim()
                    if (title.isEmpty()) continue@loop
                    out.add(
                        ServiceMenuRow.Section(
                            title = title,
                            subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                        ),
                    )
                }
                "quantity" -> {
                    val key = o.optString("key").ifBlank { "q_$i" }
                    val min = o.optInt("min", 0).coerceAtLeast(0)
                    val max = maxOf(min, o.optInt("max", 99))
                    val def = o.optInt("default_qty", 0).coerceIn(min, max)
                    val price = o.optDouble("unit_price", 0.0).coerceAtLeast(0.0)
                    out.add(
                        ServiceMenuRow.QuantityLine(
                            key = key,
                            title = o.optString("title").ifBlank { key },
                            unitLabel = o.optString("unit_label"),
                            unitPrice = price,
                            imageUrl = o.optString("image_url").takeIf { it.isNotBlank() },
                            quantity = def,
                            min = min,
                            max = max,
                        ),
                    )
                }
                "toggle" -> {
                    val key = o.optString("key").ifBlank { "t_$i" }
                    val price = o.optDouble("unit_price", 0.0).coerceAtLeast(0.0)
                    out.add(
                        ServiceMenuRow.ToggleLine(
                            key = key,
                            title = o.optString("title").ifBlank { key },
                            unitLabel = o.optString("unit_label"),
                            unitPrice = price,
                            imageUrl = o.optString("image_url").takeIf { it.isNotBlank() },
                            checked = o.optBoolean("default_checked", false),
                        ),
                    )
                }
            }
        }
        if (out.none { it is ServiceMenuRow.QuantityLine || it is ServiceMenuRow.ToggleLine }) return null
        return ParsedMenu(banner, out)
    }

    fun buildQuoteLines(rows: List<ServiceMenuRow>): List<BookingQuoteAggregator.Line> =
        rows.mapNotNull { row ->
            when (row) {
                is ServiceMenuRow.QuantityLine ->
                    if (row.quantity > 0) {
                        BookingQuoteAggregator.Line(row.title, row.unitPrice, row.quantity)
                    } else {
                        null
                    }
                is ServiceMenuRow.ToggleLine ->
                    if (row.checked) {
                        BookingQuoteAggregator.Line(row.title, row.unitPrice, 1)
                    } else {
                        null
                    }
                is ServiceMenuRow.Section -> null
            }
        }
}
