@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import io.github.artemidius.blowhard.adapters.mistral.MistralLiveTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mobile.IosCredentialStores
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
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
    private val providers = createProviderComposition()
    val runtime = ApplicationRuntime.create(
        dependencies = ApplicationRuntimeDependencies.conversationOnly(
            providers.connectionSettings,
            providers.generation,
        ),
        configuration = ApplicationRuntimeConfiguration(
            initialState = initialConversationState(),
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
                    providers.close()
                } finally {
                    closeScope.cancel()
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

private fun initialConversationState(): ConversationUiState {
    val id = ConversationId("conversation-1")
    return ConversationUiState(
        conversations = listOf(ConversationUiModel(id, "New conversation")),
        selectedConversationId = id,
    )
}
