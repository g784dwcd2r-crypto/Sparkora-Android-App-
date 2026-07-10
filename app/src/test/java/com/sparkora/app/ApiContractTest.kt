package com.sparkora.app

import com.sparkora.app.data.api.ClockEntryCreateRequest
import com.sparkora.app.data.api.ClockEntryDto
import com.sparkora.app.data.api.ClockEntryUpdateRequest
import com.sparkora.app.data.api.GeoValidateResponse
import com.sparkora.app.data.api.LoginRequest
import com.sparkora.app.data.api.PayslipDto
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.data.api.TimeOffDto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the JSON (de)serialization layer, using payload shapes the
 * real backend produces: node-postgres serves NUMERIC columns as JSON strings,
 * DATE columns as full ISO timestamps, and includes many fields the app ignores.
 * The Json configuration here must stay identical to ApiProvider's.
 */
@OptIn(ExperimentalSerializationApi::class)
class ApiContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    // ── Requests the backend validates ──────────────────────────────────────

    @Test
    fun `login request always includes the role field`() {
        // The backend rejects logins without an explicit role ("role is required").
        val encoded = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = "emma@sparkleprocleaning.co.uk", pin = "secret"),
        )
        assertTrue("role must be present: $encoded", "\"role\":\"employee\"" in encoded)
        assertTrue("\"email\":\"emma@sparkleprocleaning.co.uk\"" in encoded)
        assertTrue("\"pin\":\"secret\"" in encoded)
        // Absent company id must be omitted entirely (legacy tenant fallback).
        assertFalse("null companyId must be omitted: $encoded", "companyId" in encoded)
    }

    @Test
    fun `login request includes company id when provided`() {
        val encoded = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = "a@b.c", pin = "x", companyId = "cmp_123"),
        )
        assertTrue("\"companyId\":\"cmp_123\"" in encoded)
    }

    @Test
    fun `clock-in request carries required fields and omits nulls`() {
        val encoded = json.encodeToString(
            ClockEntryCreateRequest.serializer(),
            ClockEntryCreateRequest(
                id = "id_1",
                employeeId = "emp_1",
                clientId = null,
                clockIn = "2026-07-10T09:00:00Z",
                clockInLat = 53.4808,
                clockInLng = -2.2426,
            ),
        )
        assertTrue("\"employee_id\":\"emp_1\"" in encoded)
        assertTrue("\"clock_in\":\"2026-07-10T09:00:00Z\"" in encoded)
        assertTrue("\"clock_in_lat\":53.4808" in encoded)
        // Open session: no clock_out key at all, and no null client_id.
        assertFalse("clock_out must be omitted: $encoded", "clock_out" in encoded)
        assertFalse("null client_id must be omitted: $encoded", "client_id" in encoded)
    }

    @Test
    fun `clock-out update echoes employee and clock-in but omits absent coordinates`() {
        // The backend's update schema requires employee_id and clock_in even on
        // PUT, and COALESCEs omitted columns — so nulls must never be sent as null.
        val encoded = json.encodeToString(
            ClockEntryUpdateRequest.serializer(),
            ClockEntryUpdateRequest(
                employeeId = "emp_1",
                clockIn = "2026-07-10T09:00:00Z",
                clockOut = "2026-07-10T12:30:00Z",
            ),
        )
        assertTrue("\"employee_id\":\"emp_1\"" in encoded)
        assertTrue("\"clock_out\":\"2026-07-10T12:30:00Z\"" in encoded)
        assertFalse("absent GPS must be omitted: $encoded", "clock_out_lat" in encoded)
    }

    // ── Responses in the backend's real shapes ──────────────────────────────

    @Test
    fun `payslip parses NUMERIC columns served as strings`() {
        val payload = """
            {
              "id": "ps_1",
              "payslip_number": "PS-2026-001",
              "employee_id": "emp_1",
              "month": "2026-06",
              "period_start": "2026-06-01T00:00:00.000Z",
              "period_end": "2026-06-30T00:00:00.000Z",
              "total_hours": "152.50",
              "hourly_rate": "12.50",
              "gross_pay": "1906.25",
              "social_charges": "0.00",
              "tax_estimate": "180.10",
              "net_pay": "1726.15",
              "hour_breakdown": [{"date": "2026-06-02", "hours": 8}],
              "status": "issued",
              "created_at": "2026-07-01T10:00:00.000Z"
            }
        """.trimIndent()
        val payslip = json.decodeFromString(PayslipDto.serializer(), payload)
        assertEquals(152.50, payslip.totalHours!!, 0.001)
        assertEquals(1726.15, payslip.netPay!!, 0.001)
        assertEquals("issued", payslip.status)
    }

    @Test
    fun `payslip also parses NUMERIC columns served as numbers`() {
        val payload = """{"id":"ps_2","month":"2026-05","net_pay":1500.5,"gross_pay":1700}"""
        val payslip = json.decodeFromString(PayslipDto.serializer(), payload)
        assertEquals(1500.5, payslip.netPay!!, 0.001)
        assertEquals(1700.0, payslip.grossPay!!, 0.001)
    }

    @Test
    fun `schedule parses DATE column served as ISO timestamp`() {
        val payload = """
            {
              "id": "sch_1",
              "date": "2026-07-10T00:00:00.000Z",
              "client_id": "cli_1",
              "employee_id": "emp_1",
              "start_time": "08:00",
              "end_time": "12:00",
              "status": "scheduled",
              "payment_status": "unpaid",
              "notes": null,
              "recurrence": "none",
              "checklist": [],
              "created_at": "2026-07-01T09:00:00.000Z",
              "client_name": "Northern Tech Hub",
              "employee_name": "Emma Fields"
            }
        """.trimIndent()
        val schedule = json.decodeFromString(ScheduleDto.serializer(), payload)
        assertEquals("Northern Tech Hub", schedule.clientName)
        assertEquals("08:00", schedule.startTime)
        assertNull(schedule.notes)
    }

    @Test
    fun `clock entry parses doubles, ints and nulls in geo fields`() {
        val payload = """
            {
              "id": "ce_1",
              "employee_id": "emp_1",
              "client_id": null,
              "clock_in": "2026-07-10T09:00:12.345Z",
              "clock_out": null,
              "notes": "",
              "clock_in_lat": 53.4808,
              "clock_in_lng": -2.2426,
              "clock_out_lat": null,
              "clock_out_lng": null,
              "geo_verified": true,
              "geo_distance_m": 42,
              "geo_override": false,
              "validated_by_manager": false,
              "created_at": "2026-07-10T09:00:12.400Z"
            }
        """.trimIndent()
        val entry = json.decodeFromString(ClockEntryDto.serializer(), payload)
        assertEquals(53.4808, entry.clockInLat!!, 0.0001)
        assertEquals(42.0, entry.geoDistanceM!!, 0.001)
        assertNull(entry.clockOut)
        assertEquals(true, entry.geoVerified)
    }

    @Test
    fun `geo validation response parses both allowed and blocked shapes`() {
        val blocked = json.decodeFromString(
            GeoValidateResponse.serializer(),
            """
            {"allowed":false,"withinRadius":false,"distanceMetres":540,"radiusMetres":200,
             "geoEnabled":true,"overrideAllowed":true,"geoVerified":false,
             "clientName":"The Ivy Brasserie MCR","clientLat":53.4794,"clientLng":-2.2453}
            """.trimIndent(),
        )
        assertFalse(blocked.allowed)
        assertEquals(540.0, blocked.distanceMetres!!, 0.001)
        assertEquals(true, blocked.overrideAllowed)

        val noCoords = json.decodeFromString(
            GeoValidateResponse.serializer(),
            """{"allowed":true,"distanceMetres":null,"radiusMetres":null,"geoVerified":false,"reason":"no_client_coords"}""",
        )
        assertTrue(noCoords.allowed)
        assertNull(noCoords.distanceMetres)
        assertEquals("no_client_coords", noCoords.reason)
    }

    @Test
    fun `time off request parses NUMERIC requested_days string and DATE timestamps`() {
        val payload = """
            {
              "id": "to_1",
              "employee_id": "emp_1",
              "start_date": "2026-08-03T00:00:00.000Z",
              "end_date": "2026-08-07T00:00:00.000Z",
              "requested_days": "5.00",
              "reason": "Family holiday",
              "leave_type": "annual",
              "status": "pending",
              "created_at": "2026-07-10T11:00:00.000Z"
            }
        """.trimIndent()
        val request = json.decodeFromString(TimeOffDto.serializer(), payload)
        assertEquals(5.0, request.requestedDays!!, 0.001)
        assertEquals("pending", request.status)
    }
}
