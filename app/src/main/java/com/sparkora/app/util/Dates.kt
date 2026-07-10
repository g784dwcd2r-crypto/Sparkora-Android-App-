package com.sparkora.app.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {

    val YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DAY_LONG: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    private val DAY_SHORT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val TIME_HM: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val MONTH_LONG: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun today(): LocalDate = LocalDate.now()

    fun todayString(): String = today().format(YMD)

    /**
     * The API serves DATE columns either as "yyyy-MM-dd" (TEXT) or as a full ISO
     * timestamp ("2026-07-10T00:00:00.000Z", server runs UTC). The first ten
     * characters are the calendar date in both cases.
     */
    fun parseDay(raw: String?): LocalDate? {
        val s = raw?.trim() ?: return null
        if (s.length < 10) return null
        return try {
            LocalDate.parse(s.substring(0, 10), YMD)
        } catch (_: Exception) {
            null
        }
    }

    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun formatDayLong(date: LocalDate): String = date.format(DAY_LONG)

    fun formatDayShort(date: LocalDate): String = date.format(DAY_SHORT)

    fun formatDay(raw: String?): String =
        parseDay(raw)?.format(DAY_SHORT) ?: (raw ?: "—")

    /** "2026-07" → "July 2026" */
    fun formatMonth(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            LocalDate.parse("$raw-01", YMD).format(MONTH_LONG)
        } catch (_: Exception) {
            raw
        }
    }

    fun formatInstantTime(raw: String?): String {
        val instant = parseInstant(raw) ?: return "—"
        return instant.atZone(ZoneId.systemDefault()).format(TIME_HM)
    }

    /** "08:00" stays "08:00"; null-safe. */
    fun formatClock(hm: String?): String = hm?.takeIf { it.isNotBlank() } ?: "—"

    fun durationSince(startIso: String?, end: Instant = Instant.now()): Duration? {
        val start = parseInstant(startIso) ?: return null
        return Duration.between(start, end)
    }

    fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "%dh %02dm".format(hours, minutes)
    }
}

/** Client-generated row ID matching the web app's `id_<millis>` convention. */
fun newEntityId(): String =
    "id_${System.currentTimeMillis()}${(100..999).random()}"
