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
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.MistralTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.platform.DesktopCredentialStores
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
import io.github.artemidius.blowhard.ui.BlowhardApp

private const val PACKAGING_SMOKE_TEST_ENVIRONMENT_VARIABLE = "BLOWHARD_PACKAGING_SMOKE_TEST"
private const val PACKAGING_SMOKE_TEST_ENABLED = "1"

/** Starts the Blowhard desktop application. */
public fun main() {
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

    val title = if (connectionState.settings.mode == GenerationMode.LIVE) {
        "Blowhard — Live Mistral"
    } else {
        "Blowhard — Offline Demo"
    }
    Window(onCloseRequest = shutdown, state = windowState, title = title) {
        window.minimumSize = java.awt.Dimension(MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT)
        DesktopMenuBar(uiState, bootstrap.environment.platform, bootstrap.runtime.presenter::dispatch, shutdown)
        BlowhardApp(
            conversationPresenter = bootstrap.runtime.presenter,
            connectionSettingsPresenter = bootstrap.runtime.connectionSettingsPresenter,
        )
        if (packagingSmokeTest) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runPackagingSmokeScenario(bootstrap.runtime.presenter)
                shutdown()
            }
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
    val history = DesktopHistorySession.open(directories.configuration)
    val clipboard = AwtClipboardWriter(sessionAvailable = { environment.hasClipboardSession })
    val credentialStore = DesktopCredentialStores.mistralApiKey()
    val credentialProvider = credentialStore.asMistralCredentialProvider()
    val modelCatalog = MistralModelCatalog.create(credentialProvider)
    val connectionSettings = DefaultConnectionSettings(credentialStore, modelCatalog)
    val scenarios = FixtureScenarioRouter()
    val generation = DesktopGenerationRouter(
        demo = FixtureTextGenerationPort(scenarios::scenarioFor),
        liveFactory = { modelId ->
            val port = MistralTextGenerationPort.create(
                credentialProvider,
                MistralChatConfiguration(modelId),
            )
            OwnedGenerationPort(port, port::close)
        },
    )
    val runtime = DesktopRuntime.create(
        clipboardWriter = clipboard,
        connectionSettings = connectionSettings,
        generation = generation,
        scenarioSelection = scenarios,
        initialState = history.initialState,
        conversationStore = history.conversationStore,
        closeHistoryResources = history::close,
        closeProviderResources = {
            generation.close()
            modelCatalog.close()
        },
    )
    return DesktopBootstrap(environment, preferencesStore, preferences, runtime)
}

private data class DesktopBootstrap(
    val environment: DesktopEnvironment,
    val preferencesStore: WindowPreferencesStore,
    val preferences: WindowPreferences,
    val runtime: DesktopRuntime,
)

internal fun isPackagingSmokeTest(environment: Map<String, String>): Boolean =
    environment[PACKAGING_SMOKE_TEST_ENVIRONMENT_VARIABLE] == PACKAGING_SMOKE_TEST_ENABLED
