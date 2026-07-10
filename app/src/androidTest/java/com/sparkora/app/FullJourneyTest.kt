package com.sparkora.app

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File
import java.io.FileOutputStream
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

    /** When set, every endpoint answers 401 — simulates an expired JWT. */
    @Volatile
    private var forceUnauthorized = false

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
                if (forceUnauthorized) {
                    return json.setResponseCode(401).setBody("""{"error":"Invalid token."}""")
                }
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
                    path.startsWith("/api/time-off-requests") && request.method == "GET" ->
                        json.setBody(
                            """[{"id":"to_1","employee_id":"emp_1","start_date":"2026-08-03T00:00:00.000Z",
                                 "end_date":"2026-08-07T00:00:00.000Z","requested_days":"5.00",
                                 "reason":"Family holiday","leave_type":"annual","status":"pending"}]"""
                        )
                    path.startsWith("/api/my/payslips") ->
                        json.setBody(
                            """[{"id":"ps_1","payslip_number":"PS-2026-006","employee_id":"emp_1",
                                 "month":"2026-06","total_hours":"152.50","hourly_rate":"12.50",
                                 "gross_pay":"1906.25","social_charges":"95.31","tax_estimate":"180.10",
                                 "net_pay":"1630.84","status":"issued"}]"""
                        )
                    path.startsWith("/api/employees/") ->
                        json.setBody(
                            """{"id":"emp_1","name":"Emma Fields","email":"emma@sparkleprocleaning.co.uk",
                                "phone":"+44 7700 900205","role":"Employee","address":"1 High St",
                                "city":"Manchester","postal_code":"M1 1AA",
                                "start_date":"2024-03-01T00:00:00.000Z","contract_type":"CDI"}"""
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
        try {
            composeRule.waitUntil(timeoutMillis = timeoutMs) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            // ComposeTimeoutException extends Throwable, not Exception.
            snap("failure_${text.take(20).replace(Regex("\\W+"), "_")}")
            val onScreen = try {
                composeRule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { node ->
                        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                    }
                    .distinct()
            } catch (_: Throwable) {
                listOf("<unavailable>")
            }
            throw AssertionError(
                "Timed out waiting for text: \"$text\". Texts on screen: $onScreen", e,
            )
        }
    }

    /**
     * Best-effort screenshot into the app's external files dir; CI pulls the
     * directory afterwards and publishes it as the `app-screenshots` artifact.
     */
    private fun snap(name: String) {
        try {
            composeRule.waitForIdle()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val dir = InstrumentationRegistry.getInstrumentation()
                .targetContext.getExternalFilesDir(null) ?: return
            FileOutputStream(File(dir, "$name.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {
            // Never fail a journey over a screenshot.
        }
    }

    private fun signIn() {
        val serverUrl = server.url("/").toString()
        waitForText("Sparkora Staff", 15_000)
        snap("01_login")

        composeRule.onNodeWithText("Server settings").performClick()
        composeRule.onNodeWithText("Server URL").performTextClearance()
        composeRule.onNodeWithText("Server URL").performTextInput(serverUrl)

        composeRule.onNodeWithText("Email").performTextClearance()
        composeRule.onNodeWithText("Email").performTextInput("emma@sparkleprocleaning.co.uk")
        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Sign in").performClick()

        waitForText("Today's jobs")
        snap("02_home_today")
    }

    @Test
    fun signIn_seeJobs_clockIn_clockOut() {
        // ── Login screen → Home / Today ──────────────────────────────────────
        signIn()
        composeRule.onNodeWithText("Northern Tech Hub").assertIsDisplayed()
        composeRule.onNodeWithText("You're not clocked in").assertIsDisplayed()

        // ── Clock in (exact-match avoids "Clock in without a job") ──────────
        composeRule.onNodeWithText("Clock in").performClick()
        waitForText("On shift")
        composeRule.onNodeWithText("Clock out").assertIsDisplayed()
        snap("03_on_shift")

        // ── Clock out ────────────────────────────────────────────────────────
        composeRule.onNodeWithText("Clock out").performClick()
        waitForText("You're not clocked in")
        snap("04_clocked_out")
    }

    @Test
    fun allTabs_renderTheirData() {
        signIn()

        // Each tab must render the data served by the mock backend. Existence
        // in the semantics tree is the assertion — viewport position varies by
        // device size and is not what this journey verifies.

        // Schedule: the week grid (empty days show "No jobs") with the mock job.
        composeRule.onNodeWithText("Schedule").performClick()
        waitForText("No jobs")
        waitForText("Northern Tech Hub")
        snap("05_schedule")

        // Leave: the pending annual request with its reason, plus the request FAB.
        composeRule.onNodeWithText("Leave").performClick()
        waitForText("Annual leave")
        waitForText("Family holiday")
        waitForText("Request leave")
        snap("06_leave")

        // Pay: June payslip with formatted month and net amount.
        composeRule.onNodeWithText("Pay").performClick()
        waitForText("June 2026")
        waitForText("£1,630.84")
        snap("07_payslips")

        // Profile: employee record and sign-out affordance.
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Emma Fields")
        waitForText("Sign out")
        snap("08_profile")
    }

    @Test
    fun expiredToken_sendsTheUserBackToLogin() {
        signIn()

        // Every subsequent API call now answers 401 (expired JWT). Visiting a
        // tab triggers a fetch; the app must clear the session and return to
        // the login screen rather than strand the user on a broken view.
        forceUnauthorized = true
        composeRule.onNodeWithText("Schedule").performClick()
        waitForText("Sparkora Staff")
    }
}
