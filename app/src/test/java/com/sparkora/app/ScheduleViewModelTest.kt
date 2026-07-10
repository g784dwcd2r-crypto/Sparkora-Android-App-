package com.sparkora.app

import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.ui.schedule.ScheduleViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var vm: ScheduleViewModel
    private val requestedRanges = CopyOnWriteArrayList<String>()

    private val monday: LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val wednesday: LocalDate = monday.plusDays(2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestedRanges.add(request.path.orEmpty())
                // Dates deliberately in the backend's ISO-timestamp DATE format.
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """[
                          {"id":"s_late","date":"${monday}T00:00:00.000Z","client_id":"c1",
                           "start_time":"13:00","end_time":"17:00","status":"scheduled","client_name":"Ivy Brasserie"},
                          {"id":"s_early","date":"${monday}T00:00:00.000Z","client_id":"c2",
                           "start_time":"08:00","end_time":"12:00","status":"scheduled","client_name":"Tech Hub"},
                          {"id":"s_wed","date":"${wednesday}T00:00:00.000Z","client_id":"c3",
                           "start_time":"09:00","end_time":"11:00","status":"in-progress","client_name":"Patel Home"},
                          {"id":"s_gone","date":"${wednesday}T00:00:00.000Z","client_id":"c4",
                           "start_time":"10:00","end_time":"12:00","status":"cancelled","client_name":"Cancelled Co"}
                        ]"""
                    )
            }
        }
        server.start()
        val session = FakeSessionStore(baseUrl = server.url("/").toString())
        vm = ScheduleViewModel(SparkoraRepository(ApiProvider(session), session))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    @Test
    fun `week loads grouped by day, sorted by start time, cancelled hidden`() = runBlocking {
        val state = withTimeout(10_000) { vm.ui.first { !it.loading } }

        val mondayJobs = state.jobsByDay[monday].orEmpty()
        assertEquals(listOf("s_early", "s_late"), mondayJobs.map { it.id })

        val wednesdayJobs = state.jobsByDay[wednesday].orEmpty()
        assertEquals(listOf("s_wed"), wednesdayJobs.map { it.id })

        assertTrue(
            "cancelled jobs must not appear",
            state.jobsByDay.values.flatten().none { it.status == "cancelled" },
        )

        // The initial request covers Monday..Sunday of the current week.
        val firstRange = requestedRanges.first()
        assertTrue(firstRange, firstRange.contains("from=${monday.format(Dates.YMD)}"))
        assertTrue(firstRange, firstRange.contains("to=${monday.plusDays(6).format(Dates.YMD)}"))
    }

    @Test
    fun `week navigation requests the adjacent ranges and can return home`() = runBlocking {
        withTimeout(10_000) { vm.ui.first { !it.loading } }

        vm.nextWeek()
        val nextMonday = monday.plusWeeks(1)
        withTimeout(10_000) { vm.ui.first { !it.loading && it.weekStart == nextMonday } }
        assertTrue(requestedRanges.any { it.contains("from=${nextMonday.format(Dates.YMD)}") })

        vm.previousWeek()
        withTimeout(10_000) { vm.ui.first { !it.loading && it.weekStart == monday } }

        vm.previousWeek()
        val lastMonday = monday.minusWeeks(1)
        withTimeout(10_000) { vm.ui.first { !it.loading && it.weekStart == lastMonday } }

        vm.thisWeek()
        val home = withTimeout(10_000) { vm.ui.first { !it.loading && it.weekStart == monday } }
        assertEquals(monday, home.weekStart)
    }
}
