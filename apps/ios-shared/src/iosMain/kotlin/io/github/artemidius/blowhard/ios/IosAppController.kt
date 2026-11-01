@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import io.github.artemidius.blowhard.ui.BlowhardMobileApp
import platform.UIKit.UIViewController

/** Creates the single iOS host surface exported to Swift. */
public fun BlowhardViewController(): UIViewController {
    val owner = IosApplicationRuntime()
    lateinit var controller: UIViewController
    controller = ComposeUIViewController {
        DisposableEffect(owner) {
            onDispose {
                owner.documentPicker.unbind(controller)
                owner.close()
            }
        }
        val session by owner.session.collectAsState()
        val runtime = session.runtime
        BlowhardMobileApp(
            conversationPresenter = runtime.conversationPresenter,
            connectionSettingsPresenter = runtime.connectionSettingsPresenter,
            runInspectorPresenter = runtime.runInspectorPresenter,
            documentLibraryPresenter = runtime.documentLibraryPresenter,
            meetingNotePresenter = runtime.meetingNotePresenter,
            localDataResetPresenter = owner.localDataResetPresenter,
        )
    }
    owner.documentPicker.bind(controller)
    return controller
}
