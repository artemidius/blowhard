package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.ui.presentation.ClipboardWriteResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FailClosedConnectionSettingsTest {
    private val settings = FailClosedConnectionSettings()

    @Test
    fun `credential and provider operations fail closed`() = runTest {
        assertEquals(CredentialStatus.UNAVAILABLE, settings.state.value.credentialStatus)
        assertEquals(CredentialUpdateResult.UNAVAILABLE, settings.replaceCredential("not-stored"))
        assertEquals(CredentialUpdateResult.UNAVAILABLE, settings.deleteCredential())
        assertEquals(ModelSelectionResult.NOT_AVAILABLE, settings.selectModel("model"))
        assertEquals(ModeSelectionResult.LIVE_NOT_READY, settings.selectMode(GenerationMode.LIVE))
        assertEquals(
            ConnectionTestResult.Failure(ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE),
            settings.testConnection(),
        )
    }

    @Test
    fun `non-sensitive controls remain local`() = runTest {
        val parameters = GenerationParameters(maxOutputTokens = 1_024, temperature = 0.2)
        settings.updateGenerationParameters(parameters)
        settings.acknowledgeLiveDataDisclosure()

        assertEquals(parameters, settings.state.value.generationParameters)
        assertEquals(true, settings.state.value.liveDataDisclosureAccepted)
    }

    @Test
    fun `inactive dependency graph preserves the supplied boundary and rejects native work`() = runTest {
        val dependencies = ApplicationRuntimeDependencies.failClosed(settings)

        assertSame(settings, dependencies.connectionSettings)
        assertEquals(ClipboardWriteResult.UNAVAILABLE, dependencies.clipboardWriter.write("not copied"))
        assertEquals(DocumentListResult.StorageUnavailable, dependencies.documentLibrary.list())
        assertEquals(null, dependencies.documentPicker.select())
    }
}
