@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.artemidius.blowhard.adapters.fixture.FixtureTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.MistralChatConfiguration
import io.github.artemidius.blowhard.adapters.mistral.MistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.MistralTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.platform.DesktopCredentialStores
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
import io.github.artemidius.blowhard.ui.BlowhardApp
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import java.nio.file.Path

private const val PACKAGING_SMOKE_TEST_ENVIRONMENT_VARIABLE = "BLOWHARD_PACKAGING_SMOKE_TEST"
private const val PACKAGING_SMOKE_TEST_ENABLED = "1"
private const val REFERENCE_LIVE_SMOKE_ENVIRONMENT_VARIABLE = "BLOWHARD_REFERENCE_LIVE_SMOKE"

/** Starts the Blowhard desktop application. */
public fun main() {
    val environment = System.getenv()
    require(!(isPackagingSmokeTest(environment) && isReferenceLiveSmoke(environment))) {
        "Smoke checks must be selected individually"
    }
    if (isReferenceLiveSmoke(environment)) {
        ReferenceLiveSmoke.main(emptyArray())
        return
    }
    val bootstrap = createDesktopBootstrap()
    application { BlowhardDesktopApplication(bootstrap) }
}

@Composable
private fun ApplicationScope.BlowhardDesktopApplication(bootstrap: DesktopBootstrap) {
    val packagingSmokeTest = isPackagingSmokeTest(System.getenv())
    val windowState = rememberWindowState(
        placement = if (bootstrap.preferences.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        size = DpSize(bootstrap.preferences.width.dp, bootstrap.preferences.height.dp),
    )
    val uiState by bootstrap.runtime.presenter.state.collectAsState()
    val connectionState by bootstrap.runtime.connectionSettingsPresenter.state.collectAsState()
    var shutdownStarted by remember { mutableStateOf(false) }
    val shutdown = {
        if (!shutdownStarted) {
            shutdownStarted = true
            bootstrap.preferencesStore.save(windowState.toPreferences())
            bootstrap.runtime.close()
            exitApplication()
        }
    }

    val title = if (packagingSmokeTest) "Blowhard — Package verification" else "Blowhard — Mistral"
    Window(onCloseRequest = shutdown, state = windowState, title = title) {
        window.minimumSize = java.awt.Dimension(MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT)
        DesktopMenuBar(
            uiState,
            bootstrap.environment.platform,
            bootstrap.runtime.presenter::dispatch,
            shutdown,
            conversationEnabled = packagingSmokeTest || connectionState.settings.canUseLiveMode,
        )
        BlowhardApp(
            conversationPresenter = bootstrap.runtime.presenter,
            connectionSettingsPresenter = bootstrap.runtime.connectionSettingsPresenter,
            runInspectorPresenter = bootstrap.runtime.runInspectorPresenter,
            documentLibraryPresenter = bootstrap.runtime.documentLibraryPresenter,
            requiresConnection = !packagingSmokeTest,
        )
        PackagingSmokeEffect(packagingSmokeTest, bootstrap.runtime.presenter, shutdown)
    }
}

@Composable
private fun PackagingSmokeEffect(
    enabled: Boolean,
    presenter: io.github.artemidius.blowhard.ui.presentation.ConversationPresenter,
    shutdown: () -> Unit,
) {
    if (enabled) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runPackagingSmokeScenario(presenter)
            shutdown()
        }
    }
}

private fun androidx.compose.ui.window.WindowState.toPreferences(): WindowPreferences = normalizedWindowPreferences(
    width = size.width.value.toInt(),
    height = size.height.value.toInt(),
    maximized = placement == WindowPlacement.Maximized,
)

private fun createDesktopBootstrap(): DesktopBootstrap {
    val environment = DesktopEnvironment.current()
    val directories = AppDirectoryResolver.resolve(environment)
    val preferencesStore = WindowPreferencesStore(directories.configuration)
    val preferences = preferencesStore.load()
    val runtime = createDesktopRuntime(environment, directories.configuration)
    return DesktopBootstrap(environment, preferencesStore, preferences, runtime)
}

private fun createDesktopRuntime(environment: DesktopEnvironment, configurationDirectory: Path): DesktopRuntime {
    val packagingSmokeTest = isPackagingSmokeTest(System.getenv())
    val history = DesktopHistorySession.open(configurationDirectory)
    val documents = DesktopDocumentSession.open(configurationDirectory)
    val providers = createProviderComposition(packagingSmokeTest)
    val scenarios = FixtureScenarioRouter()
    val generation = createGenerationRouter(scenarios, providers.credentialProvider)
    val runtime = DesktopRuntime.create(
        clipboardWriter = AwtClipboardWriter(sessionAvailable = { environment.hasClipboardSession }),
        connectionSettings = providers.connectionSettings,
        generation = generation,
        scenarioSelection = scenarios,
        autoVerifyCredential = !packagingSmokeTest,
        reportSaver = AwtRunDiagnosticReportSaver(),
        initialState = history.initialState,
        conversationStore = history.conversationStore,
        closeHistoryResources = history::close,
        documentLibrary = documents.library,
        documentPicker = AwtDocumentPicker,
        closeDocumentResources = documents::close,
        closeProviderResources = {
            generation.close()
            providers.modelCatalog.close()
        },
    )
    if (!packagingSmokeTest && history.initialState.selectedConversation.messages.isNotEmpty()) {
        runtime.presenter.dispatch(ConversationAction.NewConversation)
    }
    return runtime
}

private fun createProviderComposition(packagingSmokeTest: Boolean): DesktopProviderComposition {
    val credentialStore = DesktopCredentialStores.mistralApiKey()
    val credentialProvider = credentialStore.asMistralCredentialProvider()
    val modelCatalog = MistralModelCatalog.create(credentialProvider)
    val connectionSettings = DefaultConnectionSettings(
        credentialStore,
        modelCatalog,
        startInLiveMode = !packagingSmokeTest,
    )
    return DesktopProviderComposition(connectionSettings, credentialProvider, modelCatalog)
}

private fun createGenerationRouter(
    scenarios: FixtureScenarioRouter,
    credentialProvider: MistralCredentialProvider,
): DesktopGenerationRouter = DesktopGenerationRouter(
    demo = FixtureTextGenerationPort(scenarios::scenarioFor),
    liveFactory = { modelId ->
        val port = MistralTextGenerationPort.create(credentialProvider, MistralChatConfiguration(modelId))
        OwnedGenerationPort(port, port::close)
    },
)

private data class DesktopBootstrap(
    val environment: DesktopEnvironment,
    val preferencesStore: WindowPreferencesStore,
    val preferences: WindowPreferences,
    val runtime: DesktopRuntime,
)

private data class DesktopProviderComposition(
    val connectionSettings: DefaultConnectionSettings,
    val credentialProvider: MistralCredentialProvider,
    val modelCatalog: MistralModelCatalog,
)

internal fun isPackagingSmokeTest(environment: Map<String, String>): Boolean =
    environment[PACKAGING_SMOKE_TEST_ENVIRONMENT_VARIABLE] == PACKAGING_SMOKE_TEST_ENABLED

internal fun isReferenceLiveSmoke(environment: Map<String, String>): Boolean =
    environment[REFERENCE_LIVE_SMOKE_ENVIRONMENT_VARIABLE] == PACKAGING_SMOKE_TEST_ENABLED
