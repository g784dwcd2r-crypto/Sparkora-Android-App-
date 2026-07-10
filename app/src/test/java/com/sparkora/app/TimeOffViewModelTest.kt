package com.sparkora.app

import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.ui.timeoff.TimeOffViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class TimeOffViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var vm: TimeOffViewModel
    private val recorded = CopyOnWriteArrayList<Pair<String, String>>() // method+path to body

    /** Set to a JSON body+code to script the next POST response. */
    @Volatile private var postResponseCode = 201
    @Volatile private var postResponseBody =
        """{"id":"to_new","employee_id":"emp_1","start_date":"2026-08-03","end_date":"2026-08-07",
            "requested_days":"5.00","reason":"","leave_type":"annual","status":"pending"}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded.add("${request.method} ${request.path}" to request.body.readUtf8())
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when (request.method) {
                    "GET" -> json.setBody(
                        """[{"id":"to_1","employee_id":"emp_1","start_date":"2026-07-20T00:00:00.000Z",
                             "end_date":"2026-07-22T00:00:00.000Z","requested_days":"3.00",
                             "reason":"Trip","leave_type":"annual","status":"pending"}]"""
                    )
                    "POST" -> json.setResponseCode(postResponseCode).setBody(postResponseBody)
                    "DELETE" -> json.setBody("""{"success":true}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val session = FakeSessionStore(baseUrl = server.url("/").toString())
        vm = TimeOffViewModel(SparkoraRepository(ApiProvider(session), session))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    @Test
    fun `submit sends an inclusive day count and the backend's field names`() = runBlocking {
        withTimeout(10_000) { vm.ui.first { !it.loading } }

        vm.submit(
            start = LocalDate.parse("2026-08-03"),
            end = LocalDate.parse("2026-08-07"),
            leaveType = "annual",
            reason = "  Family holiday  ",
        )
        withTimeout(10_000) { vm.ui.first { it.notice != null } }

        val (_, body) = recorded.first { (line, _) -> line.startsWith("POST /api/time-off-requests") }
        assertTrue(body, "\"start_date\":\"2026-08-03\"" in body)
        assertTrue(body, "\"end_date\":\"2026-08-07\"" in body)
        // 3rd–7th inclusive = 5 days.
        assertTrue(body, "\"requested_days\":5.0" in body)
        assertTrue(body, "\"leave_type\":\"annual\"" in body)
        // Reason is trimmed before sending.
        assertTrue(body, "\"reason\":\"Family holiday\"" in body)
    }

    @Test
    fun `overlapping request surfaces the backend's conflict message`() = runBlocking {
        withTimeout(10_000) { vm.ui.first { !it.loading } }
        postResponseCode = 409
        postResponseBody =
            """{"error":"You already have a leave request for overlapping dates. Please cancel the existing request first."}"""

        vm.submit(LocalDate.parse("2026-07-21"), LocalDate.parse("2026-07-21"), "sick", "")
        val state = withTimeout(10_000) { vm.ui.first { it.error != null } }

        assertTrue(state.error!!.contains("overlapping dates"))
    }

    @Test
    fun `cancelling a pending request deletes it and refreshes`() = runBlocking {
        val loaded = withTimeout(10_000) { vm.ui.first { !it.loading && it.requests.isNotEmpty() } }

        vm.cancel(loaded.requests.first())
        val state = withTimeout(10_000) { vm.ui.first { it.notice != null } }

        assertEquals("Request cancelled.", state.notice)
        assertTrue(recorded.any { (line, _) -> line.startsWith("DELETE /api/time-off-requests/to_1") })
        // List is re-fetched after the delete.
        assertTrue(recorded.count { (line, _) -> line.startsWith("GET /api/time-off-requests") } >= 2)
    }
}
