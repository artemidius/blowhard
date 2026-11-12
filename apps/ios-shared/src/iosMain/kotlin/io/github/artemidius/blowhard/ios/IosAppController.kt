@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.composition.FailClosedConnectionSettings
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.ui.BlowhardConnectionApp
import io.github.artemidius.blowhard.ui.presentation.ConversationUiModel
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSUUID
import platform.UIKit.UIViewController

/** Creates the single iOS host surface exported to Swift. */
public fun BlowhardViewController(): UIViewController {
    val owner = IosApplicationRuntime()
    return ComposeUIViewController {
        DisposableEffect(owner) {
            onDispose(owner::close)
        }
        BlowhardConnectionApp(owner.runtime.connectionSettingsPresenter)
    }
}

private class IosApplicationRuntime {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val runtime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies.failClosed(FailClosedConnectionSettings()),
        configuration = ApplicationRuntimeConfiguration(
            initialState = initialConversationState(),
            runIdFactory = SequentialRunIdFactory("ios-${NSUUID().UUIDString}"),
        ),
    )

    fun close() {
        closeScope.launch {
            try {
                runtime.close()
            } finally {
                closeScope.cancel()
            }
        }
    }
}

private fun initialConversationState(): ConversationUiState {
    val id = ConversationId("conversation-1")
    return ConversationUiState(
        conversations = listOf(ConversationUiModel(id, "New conversation")),
        selectedConversationId = id,
    )
}
