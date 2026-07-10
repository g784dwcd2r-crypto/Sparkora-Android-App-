package com.sparkora.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Boots the real app on a device/emulator and drives the login screen.
 * Catches whole-app failures a unit test can't: manifest/theme errors,
 * splash-screen wiring, DataStore startup, Compose runtime issues.
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForLoginScreen() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Sparkora Staff")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun appLaunches_toLoginScreen() {
        waitForLoginScreen()
        composeRule.onNodeWithText("Sparkora Staff").assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun signIn_enablesOnlyWhenCredentialsEntered() {
        waitForLoginScreen()
        composeRule.onNodeWithText("Sign in").assertIsNotEnabled()

        composeRule.onNodeWithText("Email").performTextInput("emma@example.com")
        composeRule.onNodeWithText("Sign in").assertIsNotEnabled()

        composeRule.onNodeWithText("Password").performTextInput("secret123")
        composeRule.onNodeWithText("Sign in").assertIsEnabled()
    }

    @Test
    fun serverSettings_revealsConfigurableBaseUrl() {
        waitForLoginScreen()
        composeRule.onNodeWithText("Server settings").performClick()
        composeRule.onNodeWithText("Server URL").assertIsDisplayed()
    }
}
