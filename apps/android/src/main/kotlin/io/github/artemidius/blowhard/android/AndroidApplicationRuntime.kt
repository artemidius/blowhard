package io.github.artemidius.blowhard.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mobile.AndroidCredentialStores
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
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
internal class AndroidApplicationRuntime(application: Application) : AndroidViewModel(application) {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val providers = createProviderComposition(application)
    val runtime: ApplicationRuntime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies.failClosed(providers.connectionSettings),
        configuration = ApplicationRuntimeConfiguration(
            initialState = initialConversationState(),
            runIdFactory = SequentialRunIdFactory("android-${UUID.randomUUID()}"),
            autoVerifyCredential = true,
        ),
    )

    override fun onCleared() {
        closeScope.launch {
            try {
                runtime.close()
            } finally {
                try {
                    providers.close()
                } finally {
                    closeScope.cancel()
                }
            }
        }
    }
}

private class AndroidProviderComposition(
    val connectionSettings: DefaultConnectionSettings,
    private val modelCatalog: MistralModelCatalog,
) {
    fun close(): Unit = modelCatalog.close()
}

private fun createProviderComposition(application: Application): AndroidProviderComposition {
    val credentialStore = AndroidCredentialStores.mistralApiKey(application)
    val modelCatalog = MistralModelCatalog.create(credentialStore.asMistralCredentialProvider())
    val connectionSettings = DefaultConnectionSettings(
        credentials = credentialStore,
        catalog = modelCatalog,
        startInLiveMode = true,
    )
    return AndroidProviderComposition(connectionSettings, modelCatalog)
}

private fun initialConversationState(): ConversationUiState {
    val id = ConversationId("conversation-1")
    return ConversationUiState(
        conversations = listOf(ConversationUiModel(id, "New conversation")),
        selectedConversationId = id,
    )
}
