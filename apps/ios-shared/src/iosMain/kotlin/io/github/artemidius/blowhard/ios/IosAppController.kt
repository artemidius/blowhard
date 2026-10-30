@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
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
import io.github.artemidius.blowhard.ui.BlowhardMobileApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUUID
import platform.UIKit.UIViewController

/** Creates the single iOS host surface exported to Swift. */
public fun BlowhardViewController(): UIViewController {
    val owner = IosApplicationRuntime()
    return ComposeUIViewController {
        DisposableEffect(owner) {
            onDispose(owner::close)
        }
        val runtime = owner.runtime
        BlowhardMobileApp(
            conversationPresenter = runtime.conversationPresenter,
            connectionSettingsPresenter = runtime.connectionSettingsPresenter,
            runInspectorPresenter = runtime.runInspectorPresenter,
            documentLibraryPresenter = runtime.documentLibraryPresenter,
            meetingNotePresenter = runtime.meetingNotePresenter,
        )
    }
}

private class IosApplicationRuntime {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val providers = createProviderComposition()
    private val history = runBlocking {
        ConversationHistorySession.open {
            NativeConversationStores.open(CONVERSATION_DATABASE_NAME)
        }
    }
    val runtime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies.conversationOnly(
            providers.connectionSettings,
            providers.generation,
            history.conversationStore,
        ),
        configuration = ApplicationRuntimeConfiguration(
            initialState = history.initialState,
            runIdFactory = SequentialRunIdFactory("ios-${NSUUID().UUIDString}"),
            autoVerifyCredential = true,
        ),
    )

    fun close() {
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

private class IosProviderComposition(
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

private const val CONVERSATION_DATABASE_NAME = "blowhard.db"
