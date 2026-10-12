package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.SelectableModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSettingsPresenterTest {
    @Test
    fun `initialization refreshes credential status`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)

        runCurrent()

        assertEquals(1, service.refreshCalls)
        presenter.close()
    }

    @Test
    fun `saved credential is verified on startup and failure is shown safely`() = runTest {
        val service = FakeConnectionSettings(initialCredentialStatus = CredentialStatus.CONFIGURED)
        service.connectionResult = ConnectionTestResult.Failure(ConnectionTestFailureKind.UNAUTHORIZED)
        val presenter =
            ConnectionSettingsPresenter(service, StandardTestDispatcher(testScheduler), autoVerifyCredential = true)

        runCurrent()

        assertEquals(1, service.connectionTestCalls)
        assertEquals("The provider rejected the credential.", presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `new credential is verified automatically`() = runTest {
        val service = FakeConnectionSettings()
        service.connectionResult = ConnectionTestResult.Success(listOf(SelectableModel("chat", 32_000)))
        val presenter =
            ConnectionSettingsPresenter(service, StandardTestDispatcher(testScheduler), autoVerifyCredential = true)
        runCurrent()

        presenter.dispatch(ConnectionSettingsAction.CredentialSubmitted("private-api-key"))
        runCurrent()

        assertEquals(1, service.connectionTestCalls)
        assertEquals("Connection verified. 1 compatible chat model(s) available.", presenter.state.value.notice)
        assertFalse(presenter.state.value.toString().contains("private-api-key"))
        presenter.close()
    }

    @Test
    fun `credential passes to service but never enters observable or diagnostic state`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)
        val secret = "private-api-key"
        val action = ConnectionSettingsAction.CredentialSubmitted(secret)

        presenter.dispatch(action)
        runCurrent()

        assertEquals(secret, service.replacedCredential)
        assertFalse(action.toString().contains(secret))
        assertFalse(presenter.state.value.toString().contains(secret))
        presenter.close()
    }

    @Test
    fun `connection success and failure produce sanitized feedback`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)
        service.connectionResult = ConnectionTestResult.Success(
            listOf(SelectableModel("chat", 32_000)),
        )

        presenter.dispatch(ConnectionSettingsAction.ConnectionTested)
        runCurrent()

        assertEquals("Connection verified. 1 compatible chat model(s) available.", presenter.state.value.notice)
        service.connectionResult = ConnectionTestResult.Failure(ConnectionTestFailureKind.UNAUTHORIZED)
        presenter.dispatch(ConnectionSettingsAction.ConnectionTested)
        runCurrent()
        assertEquals("The provider rejected the credential.", presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `model disclosure delete and notice actions stay serialized`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)

        presenter.dispatch(ConnectionSettingsAction.ModelSelected("chat"))
        presenter.dispatch(ConnectionSettingsAction.LiveDataDisclosureAccepted)
        presenter.dispatch(ConnectionSettingsAction.ModeSelected(GenerationMode.DEMO))
        presenter.dispatch(ConnectionSettingsAction.CredentialDeleted)
        runCurrent()

        assertEquals(listOf("chat"), service.selectedModels)
        assertTrue(service.disclosureAccepted)
        assertEquals(listOf(GenerationMode.DEMO), service.selectedModes)
        assertEquals(1, service.deleteCalls)
        assertEquals("Credential deleted.", presenter.state.value.notice)
        presenter.dispatch(ConnectionSettingsAction.NoticeDismissed)
        runCurrent()
        assertNull(presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `generation controls update typed settings for future attempts`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)
        runCurrent()

        presenter.dispatch(ConnectionSettingsAction.MaxOutputTokensSelected(1_024))
        presenter.dispatch(ConnectionSettingsAction.TemperatureSelected(0.5))
        runCurrent()

        assertEquals(
            listOf(
                GenerationParameters(maxOutputTokens = 1_024),
                GenerationParameters(maxOutputTokens = 1_024, temperature = 0.5),
            ),
            service.updatedParameters,
        )
        assertEquals(service.updatedParameters.last(), presenter.state.value.settings.generationParameters)
        presenter.close()
    }

    @Test
    fun `mode selection renders readiness and disclosure outcomes`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)
        runCurrent()

        service.modeSelectionResult = ModeSelectionResult.LIVE_NOT_READY
        presenter.dispatch(ConnectionSettingsAction.ModeSelected(GenerationMode.LIVE))
        runCurrent()
        assertEquals(
            "Verify a compatible model with the current credential before selecting live mode.",
            presenter.state.value.notice,
        )

        service.modeSelectionResult = ModeSelectionResult.DISCLOSURE_REQUIRED
        presenter.dispatch(ConnectionSettingsAction.ModeSelected(GenerationMode.LIVE))
        runCurrent()
        assertEquals(
            "Acknowledge external prompt processing before selecting live mode.",
            presenter.state.value.notice,
        )

        service.modeSelectionResult = ModeSelectionResult.UPDATED
        presenter.dispatch(ConnectionSettingsAction.ModeSelected(GenerationMode.LIVE))
        runCurrent()
        assertEquals("Live Mistral mode selected for new attempts.", presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `unexpected service exception becomes generic feedback`() = runTest {
        val service = FakeConnectionSettings(throwsOnRefresh = true)
        val presenter = presenter(service)

        runCurrent()

        assertEquals("The settings operation could not be completed safely.", presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `all connection failures render fixed sanitized feedback`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)
        val expectedMessages = mapOf(
            ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE to "Save a credential before testing the connection.",
            ConnectionTestFailureKind.UNAUTHORIZED to "The provider rejected the credential.",
            ConnectionTestFailureKind.FORBIDDEN to "The credential does not permit model discovery.",
            ConnectionTestFailureKind.PAYMENT_REQUIRED to "The provider requires an active paid account.",
            ConnectionTestFailureKind.RATE_LIMITED to "The provider rate limit was reached. Try again later.",
            ConnectionTestFailureKind.PROVIDER_UNAVAILABLE to "The provider or network is currently unavailable.",
            ConnectionTestFailureKind.INVALID_RESPONSE to "The provider returned an unsupported response.",
            ConnectionTestFailureKind.NO_COMPATIBLE_MODELS to "No explicitly compatible chat models were reported.",
            ConnectionTestFailureKind.UNEXPECTED to "The connection test failed safely.",
        )

        expectedMessages.forEach { (failure, message) ->
            service.connectionResult = ConnectionTestResult.Failure(failure)
            presenter.dispatch(ConnectionSettingsAction.ConnectionTested)
            runCurrent()
            assertEquals(message, presenter.state.value.notice)
        }
        presenter.close()
    }

    @Test
    fun `rejected settings mutations render actionable feedback`() = runTest {
        val service = FakeConnectionSettings()
        val presenter = presenter(service)
        runCurrent()

        presenter.dispatch(ConnectionSettingsAction.CredentialSubmitted(" "))
        runCurrent()
        assertEquals("Enter an API key before saving.", presenter.state.value.notice)
        assertNull(service.replacedCredential)

        service.credentialUpdateResult = CredentialUpdateResult.REJECTED
        presenter.dispatch(ConnectionSettingsAction.CredentialSubmitted("rejected"))
        runCurrent()
        assertEquals("The credential does not satisfy secure-storage requirements.", presenter.state.value.notice)

        service.deleteResult = CredentialUpdateResult.UNAVAILABLE
        presenter.dispatch(ConnectionSettingsAction.CredentialDeleted)
        runCurrent()
        assertEquals("Secure credential storage is unavailable on this device.", presenter.state.value.notice)

        service.modelSelectionResult = ModelSelectionResult.NOT_AVAILABLE
        presenter.dispatch(ConnectionSettingsAction.ModelSelected("stale"))
        runCurrent()
        assertEquals("That model is not in the latest verified catalog.", presenter.state.value.notice)
        presenter.close()
    }

    private fun kotlinx.coroutines.test.TestScope.presenter(service: ConnectionSettings): ConnectionSettingsPresenter =
        ConnectionSettingsPresenter(
            service,
            StandardTestDispatcher(testScheduler),
        )
}

private class FakeConnectionSettings(
    private val throwsOnRefresh: Boolean = false,
    private val initialCredentialStatus: CredentialStatus = CredentialStatus.MISSING,
) : ConnectionSettings {
    private val mutableState = MutableStateFlow(ConnectionSettingsState())
    override val state: StateFlow<ConnectionSettingsState> = mutableState
    var refreshCalls = 0
    var connectionTestCalls = 0
    var replacedCredential: String? = null
    var deleteCalls = 0
    var disclosureAccepted = false
    val selectedModels = mutableListOf<String>()
    val selectedModes = mutableListOf<GenerationMode>()
    val updatedParameters = mutableListOf<GenerationParameters>()
    var connectionResult: ConnectionTestResult = ConnectionTestResult.Failure(
        ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE,
    )
    var credentialUpdateResult: CredentialUpdateResult = CredentialUpdateResult.SUCCESS
    var deleteResult: CredentialUpdateResult = CredentialUpdateResult.SUCCESS
    var modelSelectionResult: ModelSelectionResult = ModelSelectionResult.UPDATED
    var modeSelectionResult: ModeSelectionResult = ModeSelectionResult.UPDATED

    override suspend fun refreshCredentialStatus() {
        refreshCalls += 1
        if (throwsOnRefresh) error("private provider detail")
        mutableState.value = mutableState.value.copy(credentialStatus = initialCredentialStatus)
    }

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult {
        replacedCredential = secret
        return credentialUpdateResult
    }

    override suspend fun deleteCredential(): CredentialUpdateResult {
        deleteCalls += 1
        return deleteResult
    }

    override suspend fun testConnection(): ConnectionTestResult {
        connectionTestCalls += 1
        return connectionResult
    }

    override suspend fun selectModel(modelId: String): ModelSelectionResult {
        selectedModels += modelId
        return modelSelectionResult
    }

    override suspend fun updateGenerationParameters(parameters: GenerationParameters) {
        updatedParameters += parameters
        mutableState.value = mutableState.value.copy(generationParameters = parameters)
    }

    override suspend fun acknowledgeLiveDataDisclosure() {
        disclosureAccepted = true
    }

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult {
        selectedModes += mode
        return modeSelectionResult
    }
}
