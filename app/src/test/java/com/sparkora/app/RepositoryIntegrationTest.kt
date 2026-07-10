package com.sparkora.app

import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.api.ClockEntryCreateRequest
import com.sparkora.app.data.repo.ApiResult
import com.sparkora.app.data.repo.SparkoraRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Runs the REAL data stack — Retrofit, OkHttp, kotlinx.serialization, repository
 * error handling — against a local mock HTTP server speaking the backend's dialect.
 */
class RepositoryIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var session: FakeSessionStore
    private lateinit var repository: SparkoraRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = FakeSessionStore(baseUrl = server.url("/").toString())
        repository = SparkoraRepository(ApiProvider(session), session)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    fun `successful login saves the session and sends the right payload`() = runBlocking {
        enqueueJson(
            200,
            """{"success":true,"role":"employee","token":"jwt-abc","employeeId":"emp_9",
                "name":"Liam Carter","lang":"en","theme":"light"}""",
        )

        val result = repository.login("liam@sparkleprocleaning.co.uk", "pass123", "cmp_42")

        assertTrue(result is ApiResult.Ok)
        val saved = session.lastSavedLogin
        assertNotNull(saved)
        assertEquals("jwt-abc", saved!!.token)
        assertEquals("emp_9", saved.employeeId)
        assertEquals("Liam Carter", saved.employeeName)

        val request = server.takeRequest()
        assertEquals("/api/auth/pin-login", request.path)
        val sent = request.body.readUtf8()
        assertTrue("role must be sent: $sent", "\"role\":\"employee\"" in sent)
        assertTrue("\"companyId\":\"cmp_42\"" in sent)
    }

    @Test
    fun `failed login surfaces the server's error message and does not clear session`() = runBlocking {
        enqueueJson(401, """{"error":"Incorrect password. Please try again."}""")

        val result = repository.login("emma@x.co", "wrong", null)

        assertTrue(result is ApiResult.Err)
        assertEquals("Incorrect password. Please try again.", (result as ApiResult.Err).message)
        assertNull(session.lastSavedLogin)
        // A pre-auth 401 must NOT nuke an existing session.
        assertEquals(0, session.clearCount)
    }

    @Test
    fun `expired subscription login returns the 402 message`() = runBlocking {
        enqueueJson(402, """{"error":"Your subscription has expired. Please renew to continue."}""")

        val result = repository.login("emma@x.co", "pw", "cmp_1")

        assertTrue(result is ApiResult.Err)
        assertTrue((result as ApiResult.Err).message.contains("subscription has expired"))
    }

    // ── Authenticated calls ──────────────────────────────────────────────────

    @Test
    fun `authenticated calls carry the bearer token`() = runBlocking {
        enqueueJson(200, "[]")

        repository.schedules("2026-07-06", "2026-07-12")

        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertTrue(request.path!!.startsWith("/api/schedules?from=2026-07-06&to=2026-07-12"))
    }

    @Test
    fun `a 401 on an authenticated call clears the stored session`() = runBlocking {
        enqueueJson(401, """{"error":"Invalid token."}""")

        val result = repository.schedules("2026-07-06", "2026-07-12")

        assertTrue(result is ApiResult.Err)
        assertEquals(1, session.clearCount)
    }

    @Test
    fun `duplicate clock-in surfaces the backend's conflict explanation`() = runBlocking {
        enqueueJson(
            409,
            """{"error":"Employee already clocked in",
                "message":"This employee already has an active clock-in session. They must clock out before starting a new session.",
                "activeEntryId":"ce_1","activeClientId":"cli_1"}""",
        )

        val result = repository.createClockEntry(
            ClockEntryCreateRequest(
                id = "id_1",
                employeeId = "emp_1",
                clockIn = "2026-07-10T09:00:00Z",
            )
        )

        assertTrue(result is ApiResult.Err)
        val err = result as ApiResult.Err
        assertEquals(409, err.code)
        assertTrue(err.message.contains("active clock-in session"))
    }

    @Test
    fun `payslips flow end-to-end through real HTTP with pg NUMERIC strings`() = runBlocking {
        enqueueJson(
            200,
            """[{"id":"ps_1","payslip_number":"PS-001","employee_id":"emp_1","month":"2026-06",
                 "total_hours":"152.50","hourly_rate":"12.50","gross_pay":"1906.25",
                 "social_charges":"95.31","tax_estimate":"180.10","net_pay":"1630.84",
                 "hour_breakdown":[],"status":"issued","created_at":"2026-07-01T10:00:00.000Z"}]""",
        )

        val result = repository.myPayslips()

        assertTrue(result is ApiResult.Ok)
        val payslip = (result as ApiResult.Ok).value.single()
        assertEquals(1630.84, payslip.netPay!!, 0.001)
        assertEquals("PS-001", payslip.payslipNumber)
    }

    @Test
    fun `unreachable server maps to a friendly connection message`() = runBlocking {
        server.shutdown()

        val result = repository.schedules("2026-07-06", "2026-07-12")

        when (result) {
            is ApiResult.Err -> assertTrue(
                result.message,
                result.message.contains("Cannot reach the server"),
            )
            is ApiResult.Ok -> fail("expected an error")
        }
    }
}
