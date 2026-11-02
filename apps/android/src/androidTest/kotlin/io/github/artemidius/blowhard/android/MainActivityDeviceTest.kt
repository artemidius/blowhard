package io.github.artemidius.blowhard.android

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level evidence that the real Android host launches its secure mobile root. */
@RunWith(AndroidJUnit4::class)
public class MainActivityDeviceTest {
    @get:Rule
    public val composeRule = createAndroidComposeRule<MainActivity>()

    /** Verifies that the mobile root exposes either its secure gate or an already verified workspace. */
    @Test
    public fun secureMobileRootLaunches(): Unit = with(composeRule) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Connect to Mistral").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Chats").fetchSemanticsNodes().isNotEmpty()
        }
        if (onAllNodesWithText("Connect to Mistral").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithText("API credential").assertIsDisplayed()
            onNodeWithText("Local data").performScrollTo().assertIsDisplayed()
        } else {
            onNodeWithText("Chats").assertIsDisplayed()
        }
    }

    /** Verifies the secure startup gate exposes a heading and announces connection-state changes. */
    @Test
    public fun secureStartupGateHasAccessibleSemantics(): Unit = with(composeRule) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Connect to Mistral").fetchSemanticsNodes().isNotEmpty()
        }

        val heading = onNodeWithText("Connect to Mistral").fetchSemanticsNode().config
        val connectionStatus = onNodeWithText("Not tested").fetchSemanticsNode().config
        assertTrue(heading.contains(SemanticsProperties.Heading))
        assertEquals(LiveRegionMode.Polite, connectionStatus[SemanticsProperties.LiveRegion])
    }

    /** Verifies configuration recreation retains the application session instead of restarting work. */
    @Test
    public fun activityRecreationRetainsApplicationRuntime(): Unit = with(composeRule) {
        lateinit var before: AndroidApplicationRuntime
        activityRule.scenario.onActivity { activity ->
            before = ViewModelProvider(activity)[AndroidApplicationRuntime::class.java]
        }

        activityRule.scenario.recreate()

        activityRule.scenario.onActivity { activity ->
            assertSame(before, ViewModelProvider(activity)[AndroidApplicationRuntime::class.java])
        }
    }
}
