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
    private val documents: AndroidDocumentSession?,
    private val providers: AndroidProviderComposition,
) {
    suspend fun close() {
        try {
            runtime.close()
        } finally {
            try {
                history.close()
            } finally {
                try {
                    documents?.close()
                } finally {
                    providers.close()
                }
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun createAndroidApplicationSession(
    context: Context,
    documentPicker: AndroidDocumentPicker,
): AndroidApplicationSession {
    val providers = createProviderComposition(context)
    var history: ConversationHistorySession? = null
    var documents: AndroidDocumentSession? = null
    return try {
        val openedHistory = openHistory(context)
        history = openedHistory
        documents = AndroidDocumentSession.openOrNull(context)
        val dependencies = createDependencies(providers, openedHistory, documents, documentPicker)
        val runtime = createRuntime(dependencies, openedHistory)
        AndroidApplicationSession(runtime, openedHistory, documents, providers)
    } catch (failure: RuntimeException) {
        closePartialSession(history, documents, providers)
        throw failure
    }
}

private suspend fun openHistory(context: Context): ConversationHistorySession = withContext(Dispatchers.IO) {
    ConversationHistorySession.open {
        AndroidConversationStores.open(context, CONVERSATION_DATABASE_NAME)
    }
}

private fun createDependencies(
    providers: AndroidProviderComposition,
    history: ConversationHistorySession,
    documents: AndroidDocumentSession?,
    documentPicker: AndroidDocumentPicker,
): ApplicationRuntimeDependencies {
    val base = ApplicationRuntimeDependencies.conversationOnly(
        providers.connectionSettings,
        providers.generation,
        history.conversationStore,
    )
    return documents?.let {
        base.withDocuments(it.library, it.ocr, documentPicker, it.processingStore)
    } ?: base
}

private fun createRuntime(
    dependencies: ApplicationRuntimeDependencies,
    history: ConversationHistorySession,
): ApplicationRuntime = ApplicationRuntime.create(
    dependencies = dependencies,
    configuration = ApplicationRuntimeConfiguration(
        initialState = history.initialState,
        runIdFactory = SequentialRunIdFactory("android-${UUID.randomUUID()}"),
        autoVerifyCredential = true,
    ),
)

private suspend fun closePartialSession(
    history: ConversationHistorySession?,
    documents: AndroidDocumentSession?,
    providers: AndroidProviderComposition,
) {
    try {
        history?.close()
    } finally {
        try {
            documents?.close()
        } finally {
            providers.close()
        }
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
