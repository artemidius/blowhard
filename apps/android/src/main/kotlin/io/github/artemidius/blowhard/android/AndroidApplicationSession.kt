package io.github.artemidius.blowhard.android

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Owns one Android runtime together with the native resources supplied to it. */
internal class AndroidApplicationSession(
    val runtime: ApplicationRuntime,
    private val history: ConversationHistorySession,
    private val providers: AndroidProviderComposition,
) {
    suspend fun close() {
        try {
            runtime.close()
        } finally {
            try {
                history.close()
            } finally {
                providers.close()
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun createAndroidApplicationSession(context: Context): AndroidApplicationSession {
    val providers = createProviderComposition(context)
    var history: ConversationHistorySession? = null
    return try {
        val openedHistory = withContext(Dispatchers.IO) {
            ConversationHistorySession.open {
                AndroidConversationStores.open(context, CONVERSATION_DATABASE_NAME)
            }
        }
        history = openedHistory
        val runtime = ApplicationRuntime.create(
            dependencies = ApplicationRuntimeDependencies.conversationOnly(
                providers.connectionSettings,
                providers.generation,
                openedHistory.conversationStore,
            ),
            configuration = ApplicationRuntimeConfiguration(
                initialState = openedHistory.initialState,
                runIdFactory = SequentialRunIdFactory("android-${UUID.randomUUID()}"),
                autoVerifyCredential = true,
            ),
        )
        AndroidApplicationSession(runtime, openedHistory, providers)
    } catch (failure: RuntimeException) {
        try {
            history?.close()
        } finally {
            providers.close()
        }
        throw failure
    }
}

internal class AndroidProviderComposition(
    val connectionSettings: DefaultConnectionSettings,
    val generation: MistralLiveTextGenerationPort,
    private val modelCatalog: MistralModelCatalog,
) {
    fun close(): Unit = modelCatalog.close()
}

private fun createProviderComposition(context: Context): AndroidProviderComposition {
    val credentialStore = AndroidCredentialStores.mistralApiKey(context)
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
