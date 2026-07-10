package com.sparkora.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * True end-to-end journey on a real Android device/emulator:
 * sign in → land on Today → see the scheduled job → clock in → live shift
 * card → clock out. The backend is a MockWebServer running on the device,
 * speaking the same JSON dialect as api.sparkora.co.uk.
 */
@RunWith(AndroidJUnit4::class)
class FullJourneyTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var server: MockWebServer

    private val today: String = LocalDate.now().toString()

    @Volatile
    private var clockedIn = false

    private fun openEntryJson() =
        """{"id":"ce_1","employee_id":"emp_1","client_id":"cli_1",
            "clock_in":"${today}T08:05:00.000Z","clock_out":null,
            "geo_verified":true,"geo_override":false}"""

    @Before
    fun startServer() {
        clockedIn = false
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    path.startsWith("/api/auth/pin-login") ->
                        json.setBody(
                            """{"success":true,"role":"employee","token":"jwt-e2e",
                                "employeeId":"emp_1","name":"Emma Fields","lang":"en","theme":"light"}"""
                        )
                    path.startsWith("/api/schedules/") && path.endsWith("/complete") ->
                        json.setBody("""{"success":true}""")
                    path.startsWith("/api/schedules") ->
                        json.setBody(
                            """[{"id":"sch_1","date":"$today","client_id":"cli_1",
                                 "employee_id":"emp_1","start_time":"08:00","end_time":"12:00",
                                 "status":"scheduled","client_name":"Northern Tech Hub"}]"""
                        )
                    path.startsWith("/api/clock-entries") && request.method == "GET" ->
                        json.setBody(if (clockedIn) "[${openEntryJson()}]" else "[]")
                    path.startsWith("/api/clock-entries") && request.method == "POST" -> {
                        clockedIn = true
                        json.setResponseCode(201).setBody(openEntryJson())
                    }
                    path.startsWith("/api/clock-entries") && request.method == "PUT" -> {
                        clockedIn = false
                        json.setBody(
                            openEntryJson().replace(
                                "\"clock_out\":null",
                                "\"clock_out\":\"${today}T12:00:00.000Z\"",
                            )
                        )
                    }
                    path.startsWith("/api/geo/validate-clock") ->
                        json.setBody(
                            """{"allowed":true,"withinRadius":true,"distanceMetres":15,
                                "radiusMetres":200,"geoEnabled":true,"overrideAllowed":true,
                                "geoVerified":true,"clientName":"Northern Tech Hub"}"""
                        )
                    path.startsWith("/api/auth/logout") -> json.setBody("{}")
                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"not found"}""")
                }
            }
        }
        server.start()
    }

    @After
    fun cleanUp() {
        // Leave no session behind for the other instrumented test classes.
        runBlocking {
            ApplicationProvider.getApplicationContext<SparkoraApp>()
                .container.session.clear()
        }
        server.shutdown()
    }

    private fun waitForText(text: String, timeoutMs: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun signIn_seeJobs_clockIn_clockOut() {
        val serverUrl = server.url("/").toString()

        // ── Login screen ─────────────────────────────────────────────────────
        waitForText("Sparkora Staff", 15_000)

        composeRule.onNodeWithText("Server settings").performClick()
        composeRule.onNodeWithText("Server URL").performTextClearance()
        composeRule.onNodeWithText("Server URL").performTextInput(serverUrl)

        composeRule.onNodeWithText("Email").performTextInput("emma@sparkleprocleaning.co.uk")
        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Sign in").performClick()

        // ── Home / Today ─────────────────────────────────────────────────────
        waitForText("Today's jobs")
        composeRule.onNodeWithText("Northern Tech Hub").assertIsDisplayed()
        composeRule.onNodeWithText("You're not clocked in").assertIsDisplayed()

        // ── Clock in (exact-match avoids "Clock in without a job") ──────────
        composeRule.onNodeWithText("Clock in").performClick()
        waitForText("On shift")
        composeRule.onNodeWithText("Clock out").assertIsDisplayed()

        // ── Clock out ────────────────────────────────────────────────────────
        composeRule.onNodeWithText("Clock out").performClick()
        waitForText("You're not clocked in")
    }
}
