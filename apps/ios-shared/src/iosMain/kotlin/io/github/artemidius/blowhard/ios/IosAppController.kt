@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import io.github.artemidius.blowhard.ui.BlowhardConnectionApp
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import kotlinx.coroutines.Dispatchers
import platform.UIKit.UIViewController

/** Creates the single iOS host surface exported to Swift. */
public fun BlowhardViewController(): UIViewController {
    val presenter = ConnectionSettingsPresenter(
        connectionSettings = IosUnavailableConnectionSettings(),
        coroutineContext = Dispatchers.Default,
    )
    return ComposeUIViewController {
        DisposableEffect(presenter) {
            onDispose(presenter::close)
        }
        BlowhardConnectionApp(presenter)
    }
}
