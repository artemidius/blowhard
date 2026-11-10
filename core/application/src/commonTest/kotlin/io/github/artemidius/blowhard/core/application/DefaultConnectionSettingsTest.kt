package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.domain.AvailableModel
import io.github.artemidius.blowhard.core.domain.CapabilitySupport
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import io.github.artemidius.blowhard.core.domain.ModelAvailability
import io.github.artemidius.blowhard.core.domain.ModelCapabilities
import io.github.artemidius.blowhard.core.domain.ModelCatalogFailure
import io.github.artemidius.blowhard.core.domain.ModelCatalogFailureKind
import io.github.artemidius.blowhard.core.domain.ModelCatalogPort
import io.github.artemidius.blowhard.core.domain.ModelCatalogResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultConnectionSettingsTest {
    @Test
    fun `credential status is exposed without credential material`() = runTest {
        val secret = "secret-value-that-must-not-escape"
        val settings = settings(credentials = FakeCredentialStore(CredentialReadResult.Available(secret)))

        settings.refreshCredentialStatus()

        assertEquals(CredentialStatus.CONFIGURED, settings.state.value.credentialStatus)
        assertFalse(settings.state.value.toString().contains(secret))
    }

    @Test
    fun `connection test conservatively exposes only available chat models`() = runTest {
        val catalog = FakeModelCatalog(
            ModelCatalogResult.Success(
                listOf(
                    model("chat", availability = ModelAvailability.AVAILABLE),
                    model("archived", availability = ModelAvailability.ARCHIVED),
                    model("unknown", availability = ModelAvailability.UNKNOWN),
                    model("embedding", chat = CapabilitySupport.UNSUPPORTED),
                    model("unspecified", chat = CapabilitySupport.UNKNOWN),
                ),
            ),
        )
        val settings = settings(catalog = catalog)
        settings.refreshCredentialStatus()

        val result = assertIs<ConnectionTestResult.Success>(settings.testConnection())

        assertEquals(listOf("chat"), result.models.map { it.id })
        assertEquals("chat", settings.state.value.selectedModelId)
        assertEquals(ConnectionStatus.READY, settings.state.value.connectionStatus)
    }

    @Test
    fun `live mode requires verified connection and disclosure acknowledgement`() = runTest {
        val settings = settings()
        settings.refreshCredentialStatus()

        assertEquals(ModeSelectionResult.LIVE_NOT_READY, settings.selectMode(GenerationMode.LIVE))
        settings.testConnection()
        assertEquals(ModeSelectionResult.DISCLOSURE_REQUIRED, settings.selectMode(GenerationMode.LIVE))

        settings.acknowledgeLiveDataDisclosure()

        assertEquals(ModeSelectionResult.UPDATED, settings.selectMode(GenerationMode.LIVE))
        assertEquals(GenerationMode.LIVE, settings.state.value.mode)
        assertTrue(settings.state.value.canUseLiveMode)
    }

    @Test
    fun `failed retest never silently falls back from live mode`() = runTest {
        val catalog = FakeModelCatalog(successfulCatalog())
        val settings = settings(catalog = catalog)
        settings.refreshCredentialStatus()
        settings.testConnection()
        settings.acknowledgeLiveDataDisclosure()
        settings.selectMode(GenerationMode.LIVE)
        catalog.result = ModelCatalogResult.Failure(
            ModelCatalogFailure(ModelCatalogFailureKind.UNAVAILABLE),
        )

        val result = assertIs<ConnectionTestResult.Failure>(settings.testConnection())

        assertEquals(ConnectionTestFailureKind.PROVIDER_UNAVAILABLE, result.kind)
        assertEquals(ConnectionStatus.FAILED, settings.state.value.connectionStatus)
        assertEquals(GenerationMode.LIVE, settings.state.value.mode)
        assertFalse(settings.state.value.canUseLiveMode)
    }

    @Test
    fun `catalog failures map to sanitized connection categories`() = runTest {
        val catalog = FakeModelCatalog(successfulCatalog())
        val settings = settings(catalog = catalog)
        settings.refreshCredentialStatus()
        val mappings = mapOf(
            ModelCatalogFailureKind.CREDENTIAL_UNAVAILABLE to
                ConnectionTestFailureKind.CREDENTIAL_UNAVAILABLE,
            ModelCatalogFailureKind.UNAUTHORIZED to ConnectionTestFailureKind.UNAUTHORIZED,
            ModelCatalogFailureKind.FORBIDDEN to ConnectionTestFailureKind.FORBIDDEN,
            ModelCatalogFailureKind.PAYMENT_REQUIRED to ConnectionTestFailureKind.PAYMENT_REQUIRED,
            ModelCatalogFailureKind.INVALID_REQUEST to ConnectionTestFailureKind.INVALID_RESPONSE,
            ModelCatalogFailureKind.NOT_FOUND to ConnectionTestFailureKind.INVALID_RESPONSE,
            ModelCatalogFailureKind.RATE_LIMITED to ConnectionTestFailureKind.RATE_LIMITED,
            ModelCatalogFailureKind.REDIRECT_REJECTED to ConnectionTestFailureKind.PROVIDER_UNAVAILABLE,
            ModelCatalogFailureKind.UNAVAILABLE to ConnectionTestFailureKind.PROVIDER_UNAVAILABLE,
            ModelCatalogFailureKind.INVALID_RESPONSE to ConnectionTestFailureKind.INVALID_RESPONSE,
            ModelCatalogFailureKind.UNEXPECTED to ConnectionTestFailureKind.UNEXPECTED,
        )

        mappings.forEach { (domainFailure, expected) ->
            catalog.result = ModelCatalogResult.Failure(ModelCatalogFailure(domainFailure))

            val actual = assertIs<ConnectionTestResult.Failure>(settings.testConnection())

            assertEquals(expected, actual.kind)
        }
    }

    @Test
    fun `catalog without explicit compatible model fails closed`() = runTest {
        val catalog = FakeModelCatalog(
            ModelCatalogResult.Success(
                listOf(model("unknown", chat = CapabilitySupport.UNKNOWN)),
            ),
        )
        val settings = settings(catalog = catalog)
        settings.refreshCredentialStatus()

        val result = assertIs<ConnectionTestResult.Failure>(settings.testConnection())

        assertEquals(ConnectionTestFailureKind.NO_COMPATIBLE_MODELS, result.kind)
        assertEquals(ConnectionStatus.FAILED, settings.state.value.connectionStatus)
        assertTrue(settings.state.value.models.isEmpty())
    }

    @Test
    fun `credential replacement invalidates connection evidence and live consent`() = runTest {
        val credentials = FakeCredentialStore(CredentialReadResult.Available("old"))
        val settings = settings(credentials = credentials)
        settings.refreshCredentialStatus()
        settings.testConnection()
        settings.acknowledgeLiveDataDisclosure()

        val result = settings.replaceCredential("new")

        assertEquals(CredentialUpdateResult.SUCCESS, result)
        assertEquals(CredentialStatus.CONFIGURED, settings.state.value.credentialStatus)
        assertEquals(ConnectionStatus.NOT_TESTED, settings.state.value.connectionStatus)
        assertTrue(settings.state.value.models.isEmpty())
        assertNull(settings.state.value.selectedModelId)
        assertFalse(settings.state.value.liveDataDisclosureAccepted)
    }

    @Test
    fun `credential deletion returns to an unconfigured demo state`() = runTest {
        val settings = settings()
        settings.refreshCredentialStatus()
        settings.testConnection()
        settings.acknowledgeLiveDataDisclosure()
        settings.selectMode(GenerationMode.LIVE)

        val result = settings.deleteCredential()

        assertEquals(CredentialUpdateResult.SUCCESS, result)
        assertEquals(CredentialStatus.MISSING, settings.state.value.credentialStatus)
        assertEquals(GenerationMode.DEMO, settings.state.value.mode)
        assertEquals(ConnectionStatus.NOT_TESTED, settings.state.value.connectionStatus)
    }

    @Test
    fun `missing and unavailable credentials clear stale connection evidence`() = runTest {
        val credentials = FakeCredentialStore(CredentialReadResult.Available("secret"))
        val settings = settings(credentials = credentials)
        settings.refreshCredentialStatus()
        settings.testConnection()
        credentials.readResult = CredentialReadResult.Missing

        settings.refreshCredentialStatus()

        assertEquals(CredentialStatus.MISSING, settings.state.value.credentialStatus)
        assertEquals(ConnectionStatus.NOT_TESTED, settings.state.value.connectionStatus)
        credentials.readResult = CredentialReadResult.Unavailable(
            io.github.artemidius.blowhard.core.domain.CredentialAccessFailure.ACCESS_DENIED,
        )
        settings.refreshCredentialStatus()
        assertEquals(CredentialStatus.UNAVAILABLE, settings.state.value.credentialStatus)
    }

    @Test
    fun `model selection rejects identifiers outside verified catalog`() = runTest {
        val settings = settings()
        settings.refreshCredentialStatus()
        settings.testConnection()

        assertEquals(ModelSelectionResult.NOT_AVAILABLE, settings.selectModel("invented"))
        assertEquals("chat-model", settings.state.value.selectedModelId)
    }

    @Test
    fun `generation parameters remain local and survive credential evidence reset`() = runTest {
        val settings = settings()
        val parameters = GenerationParameters(maxOutputTokens = 4_096, temperature = 0.5)

        settings.updateGenerationParameters(parameters)
        settings.replaceCredential("replacement")

        assertEquals(parameters, settings.state.value.generationParameters)
        assertEquals(ConnectionStatus.NOT_TESTED, settings.state.value.connectionStatus)
    }

    @Test
    fun `cancelled connection test restores prior observable state`() = runTest {
        val catalog = FakeModelCatalog(successfulCatalog())
        val settings = settings(catalog = catalog)
        settings.refreshCredentialStatus()
        val before = settings.state.value
        catalog.cancellation = CancellationException("cancelled")

        assertFailsWith<CancellationException> { settings.testConnection() }

        assertEquals(before, settings.state.value)
    }

    private fun settings(
        credentials: CredentialStore = FakeCredentialStore(CredentialReadResult.Available("secret")),
        catalog: ModelCatalogPort = FakeModelCatalog(successfulCatalog()),
    ): DefaultConnectionSettings = DefaultConnectionSettings(credentials, catalog)
}

private class FakeCredentialStore(var readResult: CredentialReadResult) : CredentialStore {
    override suspend fun read(): CredentialReadResult = readResult

    override suspend fun replace(secret: String): CredentialMutationResult {
        if (secret.isBlank()) return CredentialMutationResult.Rejected
        readResult = CredentialReadResult.Available(secret)
        return CredentialMutationResult.Success
    }

    override suspend fun delete(): CredentialMutationResult {
        readResult = CredentialReadResult.Missing
        return CredentialMutationResult.Success
    }
}

private class FakeModelCatalog(var result: ModelCatalogResult, var cancellation: CancellationException? = null) :
    ModelCatalogPort {
    override suspend fun listModels(): ModelCatalogResult {
        cancellation?.let { throw it }
        return result
    }
}

private fun successfulCatalog(): ModelCatalogResult = ModelCatalogResult.Success(
    listOf(model("chat-model")),
)

private fun model(
    id: String,
    availability: ModelAvailability = ModelAvailability.AVAILABLE,
    chat: CapabilitySupport = CapabilitySupport.SUPPORTED,
): AvailableModel = AvailableModel(
    id = id,
    aliases = emptyList(),
    contextWindowTokens = 32_000,
    availability = availability,
    capabilities = ModelCapabilities(
        chatCompletion = chat,
        fillInMiddle = CapabilitySupport.UNKNOWN,
        functionCalling = CapabilitySupport.UNKNOWN,
        fineTuning = CapabilitySupport.UNKNOWN,
        vision = CapabilitySupport.UNKNOWN,
        classification = CapabilitySupport.UNKNOWN,
    ),
)
