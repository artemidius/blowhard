package io.github.artemidius.blowhard.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.artemidius.blowhard.ui.BlowhardApp

/** Starts the Blowhard desktop application. */
public fun main(): Unit = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Blowhard",
    ) {
        window.minimumSize = java.awt.Dimension(720, 480)
        BlowhardApp()
    }
}
