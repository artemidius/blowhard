package io.github.artemidius.blowhard.composition

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
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VerifiedModelMeetingNoteGenerationRouterTest {
    @Test
    fun `router reuses one owned adapter per verified model and closes all`() = runTest {
        val settings = RouterConnectionSettings(readyState("model-a"))
        val created = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val router = VerifiedModelMeetingNoteGenerationRouter(settings) { modelId ->
            created += modelId
            OwnedMeetingNoteGenerationPort(
                generation = MeetingNoteGenerationPort {
                    MeetingNoteGenerationResult.Generated("{}", modelId)
                },
                closeAction = { closed += modelId },
            )
        }

        assertEquals("model-a", assertIs<MeetingNoteGenerationResult.Generated>(router.generate(request())).modelId)
        assertEquals("model-a", assertIs<MeetingNoteGenerationResult.Generated>(router.generate(request())).modelId)
        settings.set(readyState("model-b"))
        assertEquals("model-b", assertIs<MeetingNoteGenerationResult.Generated>(router.generate(request())).modelId)

        assertEquals(listOf("model-a", "model-b"), created)
        router.close()
        router.close()
        assertEquals(listOf("model-a", "model-b"), closed)
        assertEquals(
            MeetingNoteGenerationFailureKind.REMOTE_FAILURE,
            assertIs<MeetingNoteGenerationResult.Failed>(router.generate(request())).kind,
        )
    }

    @Test
    fun `router fails closed before a model is verified`() = runTest {
        val router = VerifiedModelMeetingNoteGenerationRouter(RouterConnectionSettings(ConnectionSettingsState())) {
            error("factory must not run")
        }

        assertEquals(
            MeetingNoteGenerationFailureKind.AUTHENTICATION,
            assertIs<MeetingNoteGenerationResult.Failed>(router.generate(request())).kind,
        )
    }
}

private class RouterConnectionSettings(initial: ConnectionSettingsState) : ConnectionSettings {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ConnectionSettingsState> = mutableState

    fun set(value: ConnectionSettingsState) {
        mutableState.value = value
    }

    override suspend fun refreshCredentialStatus(): Unit = unsupported()
    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = unsupported()
    override suspend fun deleteCredential(): CredentialUpdateResult = unsupported()
    override suspend fun testConnection(): ConnectionTestResult = unsupported()
    override suspend fun selectModel(modelId: String): ModelSelectionResult = unsupported()
    override suspend fun updateGenerationParameters(parameters: GenerationParameters): Unit = unsupported()
    override suspend fun acknowledgeLiveDataDisclosure(): Unit = unsupported()
    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = unsupported()
}

private fun readyState(modelId: String): ConnectionSettingsState = ConnectionSettingsState(
    mode = GenerationMode.LIVE,
    credentialStatus = CredentialStatus.CONFIGURED,
    connectionStatus = ConnectionStatus.READY,
    models = listOf(SelectableModel(modelId, contextWindowTokens = 32_000)),
    selectedModelId = modelId,
    liveDataDisclosureAccepted = true,
)

private fun request(): MeetingNoteGenerationRequest = MeetingNoteGenerationRequest(
    instructions = "Extract meeting notes",
    sourceJson = "{}",
    schemaName = "meeting_note_v1",
    schemaJson = "{}",
    maximumOutputTokens = 256,
)

private fun unsupported(): Nothing = error("unused test operation")
