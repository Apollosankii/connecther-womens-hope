package com.womanglobal.connecther.utils

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object JobDateUtils {

    private val formatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    }

    /** Parses Supabase / PostgREST ISO-8601 timestamps; returns original string if parse fails. */
    fun formatForDisplay(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val s = iso.trim()
        val instant = runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s.replaceFirst(' ', 'T')).toInstant() }.getOrNull()
        return instant?.let { formatter.format(it) } ?: s
    }
}
