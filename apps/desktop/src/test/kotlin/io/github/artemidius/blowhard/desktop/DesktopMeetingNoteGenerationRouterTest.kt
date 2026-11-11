package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.SelectableModel
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopMeetingNoteGenerationRouterTest {
    @Test
    fun `verified selected model is cached and closed once`() = runTest {
        var factoryCalls = 0
        var closeCalls = 0
        val router = DesktopMeetingNoteGenerationRouter(ReadyConnectionSettings()) { modelId ->
            assertEquals("model", modelId)
            factoryCalls += 1
            OwnedMeetingNoteGenerationPort(
                generation = { MeetingNoteGenerationResult.Generated("{}", modelId) },
                closeAction = { closeCalls += 1 },
            )
        }

        assertIs<MeetingNoteGenerationResult.Generated>(router.generate(request()))
        assertIs<MeetingNoteGenerationResult.Generated>(router.generate(request()))
        router.close()
        router.close()

        assertEquals(1, factoryCalls)
        assertEquals(1, closeCalls)
    }

    @Test
    fun `unverified configuration and closed router fail without provider construction`() = runTest {
        var factoryCalls = 0
        val settings = ReadyConnectionSettings(ConnectionSettingsState())
        val router = DesktopMeetingNoteGenerationRouter(settings) {
            factoryCalls += 1
            error("must not run")
        }

        val unavailable = assertIs<MeetingNoteGenerationResult.Failed>(router.generate(request()))
        assertEquals(MeetingNoteGenerationFailureKind.AUTHENTICATION, unavailable.kind)
        router.close()
        val closed = assertIs<MeetingNoteGenerationResult.Failed>(router.generate(request()))
        assertEquals(MeetingNoteGenerationFailureKind.REMOTE_FAILURE, closed.kind)
        assertEquals(0, factoryCalls)
    }
}

private class ReadyConnectionSettings(
    initial: ConnectionSettingsState = ConnectionSettingsState(
        mode = GenerationMode.LIVE,
        credentialStatus = CredentialStatus.CONFIGURED,
        connectionStatus = ConnectionStatus.READY,
        models = listOf(SelectableModel("model", 1_000)),
        selectedModelId = "model",
        liveDataDisclosureAccepted = true,
    ),
) : ConnectionSettings {
    override val state = MutableStateFlow(initial)
    override suspend fun refreshCredentialStatus() = Unit
    override suspend fun replaceCredential(secret: String) = CredentialUpdateResult.SUCCESS
    override suspend fun deleteCredential() = CredentialUpdateResult.SUCCESS
    override suspend fun testConnection() = ConnectionTestResult.Success(state.value.models)
    override suspend fun selectModel(modelId: String) = ModelSelectionResult.UPDATED
    override suspend fun updateGenerationParameters(parameters: GenerationParameters) = Unit
    override suspend fun acknowledgeLiveDataDisclosure() = Unit
    override suspend fun selectMode(mode: GenerationMode) = ModeSelectionResult.UPDATED
}

private fun request(): MeetingNoteGenerationRequest = MeetingNoteGenerationRequest(
    instructions = "instructions",
    sourceJson = "{}",
    schemaName = "note",
    schemaJson = "{}",
    maximumOutputTokens = 1,
)
