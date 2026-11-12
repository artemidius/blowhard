package io.github.artemidius.blowhard.android

import androidx.lifecycle.ViewModel
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.composition.FailClosedConnectionSettings
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import io.github.artemidius.blowhard.ui.presentation.ConversationUiModel
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/** Retains application-owned services across Android activity recreation. */
internal class AndroidApplicationRuntime : ViewModel() {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val runtime: ApplicationRuntime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies.failClosed(FailClosedConnectionSettings()),
        configuration = ApplicationRuntimeConfiguration(
            initialState = initialConversationState(),
            runIdFactory = SequentialRunIdFactory("android-${UUID.randomUUID()}"),
        ),
    )

    override fun onCleared() {
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
