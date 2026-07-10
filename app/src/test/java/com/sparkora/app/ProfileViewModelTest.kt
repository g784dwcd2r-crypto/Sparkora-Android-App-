package com.sparkora.app

import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var session: FakeSessionStore

    /** Simulate a broken backend for the logout endpoint. */
    @Volatile private var logoutResponseCode = 200

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    request.path.orEmpty().startsWith("/api/employees/") ->
                        json.setBody(
                            """{"id":"emp_1","name":"Emma Fields","email":"emma@sparkleprocleaning.co.uk",
                                "phone":"+44 7700 900205","role":"Employee","hourly_rate":"13.25",
                                "weekly_hours":"40.00","address":"1 High St","city":"Manchester",
                                "postal_code":"M1 1AA","country":"UK","start_date":"2024-03-01T00:00:00.000Z",
                                "status":"active","contract_type":"CDI"}"""
                        )
                    request.path.orEmpty().startsWith("/api/auth/logout") ->
                        json.setResponseCode(logoutResponseCode).setBody("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        session = FakeSessionStore(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun newViewModel() =
        ProfileViewModel(SparkoraRepository(ApiProvider(session), session))

    @Test
    fun `profile loads the employee record with pg NUMERIC fields`() = runBlocking {
        val vm = newViewModel()
        val state = withTimeout(10_000) { vm.ui.first { !it.loading } }

        assertEquals("Emma Fields", state.profile?.name)
        assertEquals(13.25, state.profile?.hourlyRate!!, 0.001)
        assertEquals("CDI", state.profile?.contractType)
    }

    @Test
    fun `logout clears the local session even when the server errors`() = runBlocking {
        logoutResponseCode = 500
        val vm = newViewModel()
        withTimeout(10_000) { vm.ui.first { !it.loading } }

        vm.logout()
        withTimeout(10_000) { while (session.clearCount == 0) delay(25) }

        assertEquals(1, session.clearCount)
        assertEquals(null, session.cachedToken)
    }
}
