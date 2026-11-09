package io.github.artemidius.blowhard.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.artemidius.blowhard.ui.BlowhardApp

private const val MINIMUM_WINDOW_WIDTH = 720
private const val MINIMUM_WINDOW_HEIGHT = 480
private const val PACKAGING_SMOKE_TEST_ENVIRONMENT_VARIABLE = "BLOWHARD_PACKAGING_SMOKE_TEST"
private const val PACKAGING_SMOKE_TEST_ENABLED = "1"

/** Starts the Blowhard desktop application. */
public fun main(): Unit = application {
    val packagingSmokeTest = isPackagingSmokeTest(System.getenv())

    Window(
        onCloseRequest = ::exitApplication,
        title = "Blowhard",
    ) {
        window.minimumSize = java.awt.Dimension(MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT)
        BlowhardApp()

        if (packagingSmokeTest) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                exitApplication()
            }
        }
    }
}

internal fun isPackagingSmokeTest(environment: Map<String, String>): Boolean =
    environment[PACKAGING_SMOKE_TEST_ENVIRONMENT_VARIABLE] == PACKAGING_SMOKE_TEST_ENABLED
