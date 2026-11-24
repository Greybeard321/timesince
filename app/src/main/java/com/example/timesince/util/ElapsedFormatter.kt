package com.example.timesince.util

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ElapsedDisplay(
    val compact: String,
    val totalDays: String,
)

object ElapsedFormatter {
    fun format(start: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): ElapsedDisplay {
        val safeToday = if (today.isBefore(start)) start else today
        val period = Period.between(start, safeToday)
        val parts = buildList {
            if (period.years > 0) add("${period.years}y")
            if (period.months > 0) add("${period.months}m")
            if (period.days > 0) add("${period.days}d")
        }
        val compact = when {
            start.isEqual(safeToday) -> "Today"
            parts.isEmpty() -> "0d"
            else -> parts.joinToString(" ")
        }
        val days = ChronoUnit.DAYS.between(start, safeToday)
        val totalDays = String.format(locale, "%,d day%s total", days, if (days == 1L) "" else "s")
        return ElapsedDisplay(compact = compact, totalDays = totalDays)
    }

    fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
