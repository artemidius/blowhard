package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.mistral.MistralLiveTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mobile.IosCredentialStores
import io.github.artemidius.blowhard.adapters.persistence.NativeConversationStores
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.composition.ConversationHistorySession
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import platform.Foundation.NSUUID

/** Owns one iOS runtime together with the native resources supplied to it. */
internal class IosApplicationSession(
    val runtime: ApplicationRuntime,
    private val history: ConversationHistorySession,
    private val providers: IosProviderComposition,
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
internal suspend fun createIosApplicationSession(): IosApplicationSession {
    val providers = createProviderComposition()
    var history: ConversationHistorySession? = null
    return try {
        val openedHistory = ConversationHistorySession.open {
            NativeConversationStores.open(CONVERSATION_DATABASE_NAME)
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
                runIdFactory = SequentialRunIdFactory("ios-${NSUUID().UUIDString}"),
                autoVerifyCredential = true,
            ),
        )
        IosApplicationSession(runtime, openedHistory, providers)
    } catch (failure: RuntimeException) {
        try {
            history?.close()
        } finally {
            providers.close()
        }
        throw failure
    }
}

internal class IosProviderComposition(
    val connectionSettings: DefaultConnectionSettings,
    val generation: MistralLiveTextGenerationPort,
    private val modelCatalog: MistralModelCatalog,
) {
    fun close(): Unit = modelCatalog.close()
}

private fun createProviderComposition(): IosProviderComposition {
    val credentialStore = IosCredentialStores.mistralApiKey()
    val credentialProvider = credentialStore.asMistralCredentialProvider()
    val modelCatalog = MistralModelCatalog.create(credentialProvider)
    val connectionSettings = DefaultConnectionSettings(
        credentials = credentialStore,
        catalog = modelCatalog,
        startInLiveMode = true,
    )
    return IosProviderComposition(
        connectionSettings = connectionSettings,
        generation = MistralLiveTextGenerationPort.create(credentialProvider),
        modelCatalog = modelCatalog,
    )
}
