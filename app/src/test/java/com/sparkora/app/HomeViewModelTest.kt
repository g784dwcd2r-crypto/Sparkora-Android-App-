package com.sparkora.app

import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.api.ScheduleDto
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.ui.home.HomeViewModel
import com.sparkora.app.util.Dates
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the clock in/out state machine through the real repository + HTTP
 * stack, with a scripted backend. Verifies the geofence decisions:
 * blocked, override-allowed, and the happy path — plus clock-out completing
 * the matching job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var session: FakeSessionStore
    private lateinit var location: FakeLocationProvider
    private lateinit var repository: SparkoraRepository

    /** Scripted backend state. */
    private var geoResponse: String = """{"allowed":true,"geoVerified":true,"distanceMetres":12,"radiusMetres":200,"overrideAllowed":true}"""
    private var clockedIn = false
    private val today = Dates.todayString()

    private fun openEntryJson() =
        """{"id":"ce_open","employee_id":"emp_1","client_id":"cli_1",
            "clock_in":"${today}T08:05:00.000Z","clock_out":null,
            "geo_verified":true,"geo_override":false}"""

    private fun scheduleJson() =
        """[{"id":"sch_1","date":"$today","client_id":"cli_1","employee_id":"emp_1",
             "start_time":"08:00","end_time":"12:00","status":"scheduled",
             "client_name":"Northern Tech Hub"}]"""

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    path.startsWith("/api/schedules/") && path.endsWith("/complete") ->
                        json.setBody("""{"success":true}""")
                    path.startsWith("/api/schedules") -> json.setBody(scheduleJson())
                    path.startsWith("/api/clock-entries") && request.method == "GET" ->
                        json.setBody(if (clockedIn) "[${openEntryJson()}]" else "[]")
                    path.startsWith("/api/clock-entries") && request.method == "POST" -> {
                        clockedIn = true
                        json.setResponseCode(201).setBody(openEntryJson())
                    }
                    path.startsWith("/api/clock-entries") && request.method == "PUT" -> {
                        clockedIn = false
                        json.setBody(openEntryJson().replace("\"clock_out\":null", "\"clock_out\":\"${today}T12:00:00.000Z\""))
                    }
                    path.startsWith("/api/geo/validate-clock") -> json.setBody(geoResponse)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        session = FakeSessionStore(baseUrl = server.url("/").toString())
        location = FakeLocationProvider()
        repository = SparkoraRepository(ApiProvider(session), session)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun newViewModel() = HomeViewModel(repository, session, location)

    private fun job() = ScheduleDto(
        id = "sch_1",
        date = today,
        clientId = "cli_1",
        employeeId = "emp_1",
        startTime = "08:00",
        endTime = "12:00",
        status = "scheduled",
        clientName = "Northern Tech Hub",
    )

    private fun recordedPaths(): List<String> {
        val paths = mutableListOf<String>()
        while (true) {
            val request = server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            paths.add("${request.method} ${request.path}")
        }
        return paths
    }

    @Test
    fun `geofence block without override permission refuses the clock-in`() = runBlocking {
        geoResponse = """{"allowed":false,"withinRadius":false,"distanceMetres":540,
                          "radiusMetres":200,"geoEnabled":true,"overrideAllowed":false,"geoVerified":false}"""
        val vm = newViewModel()
        withTimeout(10_000) { vm.ui.first { !it.loading } }

        vm.clockIn(job())
        val state = withTimeout(10_000) { vm.ui.first { it.error != null } }

        assertTrue(state.error!!, state.error!!.contains("Too far"))
        assertTrue(state.error!!.contains("540"))
        // The clock entry must never have been created.
        val posts = recordedPaths().filter { it.startsWith("POST /api/clock-entries") }
        assertEquals(emptyList<String>(), posts)
    }

    @Test
    fun `geofence block with override permission asks first, then records the override`() = runBlocking {
        geoResponse = """{"allowed":false,"withinRadius":false,"distanceMetres":320,
                          "radiusMetres":200,"geoEnabled":true,"overrideAllowed":true,"geoVerified":false}"""
        val vm = newViewModel()
        withTimeout(10_000) { vm.ui.first { !it.loading } }

        vm.clockIn(job())
        val prompted = withTimeout(10_000) { vm.ui.first { it.overridePrompt != null } }
        assertTrue(prompted.overridePrompt!!.message.contains("320m"))

        vm.confirmOverride()
        withTimeout(10_000) { vm.ui.first { it.activeEntry != null } }

        val post = recordedPaths().first { it.startsWith("POST /api/clock-entries") }
        assertNotNull(post)
    }

    @Test
    fun `happy path clock-in captures GPS and becomes the active shift`() = runBlocking {
        val vm = newViewModel()
        withTimeout(10_000) { vm.ui.first { !it.loading } }

        vm.clockIn(job())
        val state = withTimeout(10_000) { vm.ui.first { it.activeEntry != null } }

        assertEquals("ce_open", state.activeEntry!!.id)
        assertNotNull(state.notice)
        assertTrue(state.notice!!.contains("Northern Tech Hub"))
    }

    @Test
    fun `clock-out closes the shift and completes the matching job`() = runBlocking {
        clockedIn = true
        val vm = newViewModel()
        withTimeout(10_000) { vm.ui.first { !it.loading && it.activeEntry != null } }

        vm.clockOut()
        val state = withTimeout(10_000) { vm.ui.first { it.notice != null } }

        assertTrue(state.notice!!, state.notice!!.startsWith("Clocked out"))
        val paths = recordedPaths()
        assertTrue(paths.any { it.startsWith("PUT /api/clock-entries/ce_open") })
        assertTrue(
            "schedule should be marked complete: $paths",
            paths.any { it == "PATCH /api/schedules/sch_1/complete" },
        )
    }
}
