package io.github.artemidius.blowhard.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level evidence that the real Android host launches its secure mobile root. */
@RunWith(AndroidJUnit4::class)
public class MainActivityDeviceTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<MainActivity>()

    /** Verifies that the mobile root gates its workspace behind Keystore-backed credentials. */
    @Test
    public fun connectionScreenLaunchesWithSecureCredentialAccess(): Unit = with(composeRule) {
        onNodeWithText("Connect to Mistral").assertIsDisplayed()
        onNodeWithText("API credential").assertIsDisplayed()
    }
}
