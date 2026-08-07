package io.github.artemidius.blowhard.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.artemidius.blowhard.ui.BlowhardApp

private const val MINIMUM_WINDOW_WIDTH = 720
private const val MINIMUM_WINDOW_HEIGHT = 480

/** Starts the Blowhard desktop application. */
public fun main(): Unit = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Blowhard",
    ) {
        window.minimumSize = java.awt.Dimension(MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT)
        BlowhardApp()
    }
}
