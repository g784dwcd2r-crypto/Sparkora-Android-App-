package com.sparkora.app

import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.ui.login.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var session: FakeSessionStore
    private lateinit var vm: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        session = FakeSessionStore(baseUrl = server.url("/").toString(), signedIn = false)
        vm = LoginViewModel(SparkoraRepository(ApiProvider(session), session), session)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    @Test
    fun `prefills saved email, company and server`() = runBlocking {
        val state = withTimeout(5_000) { vm.ui.first { it.email.isNotBlank() } }
        assertEquals("emma@sparkleprocleaning.co.uk", state.email)
        assertEquals("cmp_1", state.companyId)
        assertEquals(session.cachedBaseUrl, state.serverUrl)
    }

    @Test
    fun `successful sign-in stores the session`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"success":true,"role":"employee","token":"jwt-1","employeeId":"emp_1",
                        "name":"Emma Fields","lang":"en","theme":"light"}"""
                )
        )
        vm.onEmailChange("emma@sparkleprocleaning.co.uk")
        vm.onPasswordChange("secret123")

        vm.login()
        // The success path hands over to the root composable without another UI
        // emission, so poll the session store instead of the state flow.
        withTimeout(10_000) { while (session.lastSavedLogin == null) delay(25) }

        val saved = session.lastSavedLogin!!
        assertEquals("jwt-1", saved.token)
        assertEquals("emp_1", saved.employeeId)
        assertNotNull(session.cachedToken)
    }

    @Test
    fun `wrong password shows the backend's message and stays signed out`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Incorrect password. Please try again."}""")
        )
        vm.onEmailChange("emma@sparkleprocleaning.co.uk")
        vm.onPasswordChange("nope")

        vm.login()
        val state = withTimeout(10_000) { vm.ui.first { it.error != null } }

        assertEquals("Incorrect password. Please try again.", state.error)
        assertNull(session.lastSavedLogin)
        assertEquals(false, state.busy)
    }
}
