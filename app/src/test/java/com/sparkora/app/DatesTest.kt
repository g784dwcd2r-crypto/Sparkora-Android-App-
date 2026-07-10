package com.sparkora.app

import com.sparkora.app.util.Dates
import com.sparkora.app.util.newEntityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class DatesTest {

    @Test
    fun `parseDay handles plain dates and pg ISO timestamps`() {
        val expected = LocalDate.of(2026, 7, 10)
        assertEquals(expected, Dates.parseDay("2026-07-10"))
        assertEquals(expected, Dates.parseDay("2026-07-10T00:00:00.000Z"))
        assertEquals(expected, Dates.parseDay("2026-07-10T23:59:59Z"))
    }

    @Test
    fun `parseDay rejects garbage without throwing`() {
        assertNull(Dates.parseDay(null))
        assertNull(Dates.parseDay(""))
        assertNull(Dates.parseDay("tomorrow"))
        assertNull(Dates.parseDay("2026-7-1"))
    }

    @Test
    fun `parseInstant reads pg timestamptz output`() {
        assertEquals(
            Instant.parse("2026-07-10T09:00:12.345Z"),
            Dates.parseInstant("2026-07-10T09:00:12.345Z"),
        )
        assertNull(Dates.parseInstant("not a time"))
        assertNull(Dates.parseInstant(null))
    }

    @Test
    fun `formatMonth turns payslip months into readable labels`() {
        assertEquals("July 2026", Dates.formatMonth("2026-07"))
        assertEquals("—", Dates.formatMonth(null))
        // Unparseable values fall back to the raw string rather than crashing.
        assertEquals("Q3-2026", Dates.formatMonth("Q3-2026"))
    }

    @Test
    fun `formatDuration renders hours and minutes`() {
        assertEquals("3h 25m", Dates.formatDuration(Duration.ofMinutes(205)))
        assertEquals("0h 00m", Dates.formatDuration(Duration.ZERO))
        // Clock skew must never render a negative shift length.
        assertEquals("0h 00m", Dates.formatDuration(Duration.ofMinutes(-10)))
    }

    @Test
    fun `durationSince measures open shifts`() {
        val start = "2026-07-10T09:00:00Z"
        val now = Instant.parse("2026-07-10T12:30:00Z")
        assertEquals(Duration.ofMinutes(210), Dates.durationSince(start, now))
        assertNull(Dates.durationSince("bogus"))
    }

    @Test
    fun `entity ids follow the web app's convention and do not repeat`() {
        val a = newEntityId()
        val b = newEntityId()
        assertTrue(a.startsWith("id_"))
        assertTrue(a.length <= 100) // backend column limit
        assertNotEquals(a, b)
    }
}
