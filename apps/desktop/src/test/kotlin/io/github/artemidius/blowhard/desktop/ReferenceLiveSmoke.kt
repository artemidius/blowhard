package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.mistral.MistralChatConfiguration
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.MistralTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.platform.DesktopCredentialStores
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
import io.github.artemidius.blowhard.core.domain.CredentialStore
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriter
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationRunState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/** Trusted, opt-in check of the real desktop composition path with a synthetic prompt. */
internal object ReferenceLiveSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)) {
            println("Live smoke unavailable: macOS Keychain is required.")
            exitProcess(2)
        }
        val passed = try {
            runBlocking { verifyLiveConversation() }
            true
        } catch (_: Exception) {
            false
        }
        println(if (passed) "Live smoke passed: catalog and bounded chat completed." else "Live smoke failed.")
        if (!passed) exitProcess(1)
    }

    private suspend fun verifyLiveConversation() {
        val credentials = DesktopCredentialStores.mistralApiKey()
        val credentialProvider = credentials.asMistralCredentialProvider()
        val catalog = MistralModelCatalog.create(credentialProvider)
        val generation = DesktopGenerationRouter(
            demo = { error("Fixture generation is unavailable in this check") },
            liveFactory = { modelId ->
                val port = MistralTextGenerationPort.create(credentialProvider, MistralChatConfiguration(modelId))
                OwnedGenerationPort(port, port::close)
            },
        )
        var runtime: DesktopRuntime? = null
        try {
            withTimeout(45.seconds) {
                val settings = verifiedSettings(credentials, catalog)
                runtime = DesktopRuntime.create(
                    clipboardWriter = ClipboardWriter { ClipboardWriteResult.UNAVAILABLE },
                    connectionSettings = settings,
                    generation = generation,
                    scenarioSelection = { _, _ -> },
                )
                runOneConversation(checkNotNull(runtime))
            }
        } finally {
            runtime?.close()
            generation.close()
            catalog.close()
        }
    }

    private suspend fun verifiedSettings(
        credentials: CredentialStore,
        catalog: MistralModelCatalog,
    ): DefaultConnectionSettings = DefaultConnectionSettings(credentials, catalog, startInLiveMode = true).apply {
        refreshCredentialStatus()
        val result = testConnection()
        check(result is ConnectionTestResult.Success && result.models.any { it.id == REFERENCE_MODEL })
        check(selectModel(REFERENCE_MODEL) == ModelSelectionResult.UPDATED)
        updateGenerationParameters(GenerationParameters(maxOutputTokens = 24))
    }

    private suspend fun runOneConversation(runtime: DesktopRuntime) {
        val presenter = runtime.presenter
        presenter.dispatch(ConversationAction.ComposerChanged("Reply with the single word READY."))
        presenter.dispatch(ConversationAction.Send)
        val terminal = presenter.state.first { it.selectedConversation.runState in TERMINAL_STATES }
        check(terminal.selectedConversation.runState == ConversationRunState.SUCCEEDED)
        check(terminal.selectedConversation.messages.last().text.isNotBlank())
    }
}

private val TERMINAL_STATES = setOf(
    ConversationRunState.SUCCEEDED,
    ConversationRunState.FAILED,
    ConversationRunState.CANCELLED,
    ConversationRunState.INTERRUPTED,
)

private const val REFERENCE_MODEL = "mistral-small-latest"
