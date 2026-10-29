package io.github.artemidius.blowhard.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.artemidius.blowhard.adapters.mistral.MistralLiveTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mobile.AndroidCredentialStores
import io.github.artemidius.blowhard.adapters.persistence.AndroidConversationStores
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.composition.ConversationHistorySession
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Retains application-owned services across Android activity recreation. */
internal class AndroidApplicationRuntime(application: Application) : AndroidViewModel(application) {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val providers = createProviderComposition(application)
    private val history = runBlocking(Dispatchers.IO) {
        ConversationHistorySession.open {
            AndroidConversationStores.open(application, CONVERSATION_DATABASE_NAME)
        }
    }
    val runtime: ApplicationRuntime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies.conversationOnly(
            providers.connectionSettings,
            providers.generation,
            history.conversationStore,
        ),
        configuration = ApplicationRuntimeConfiguration(
            initialState = history.initialState,
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
                    history.close()
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
}

private class AndroidProviderComposition(
    val connectionSettings: DefaultConnectionSettings,
    val generation: MistralLiveTextGenerationPort,
    private val modelCatalog: MistralModelCatalog,
) {
    fun close(): Unit = modelCatalog.close()
}

private fun createProviderComposition(application: Application): AndroidProviderComposition {
    val credentialStore = AndroidCredentialStores.mistralApiKey(application)
    val credentialProvider = credentialStore.asMistralCredentialProvider()
    val modelCatalog = MistralModelCatalog.create(credentialProvider)
    val connectionSettings = DefaultConnectionSettings(
        credentials = credentialStore,
        catalog = modelCatalog,
        startInLiveMode = true,
    )
    return AndroidProviderComposition(
        connectionSettings = connectionSettings,
        generation = MistralLiveTextGenerationPort.create(credentialProvider),
        modelCatalog = modelCatalog,
    )
}

private const val CONVERSATION_DATABASE_NAME = "blowhard.db"
