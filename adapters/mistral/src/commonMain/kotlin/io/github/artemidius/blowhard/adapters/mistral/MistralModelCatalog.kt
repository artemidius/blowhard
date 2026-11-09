package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.ModelCatalogFailureKind
import io.github.artemidius.blowhard.core.domain.ModelCatalogPort
import io.github.artemidius.blowhard.core.domain.ModelCatalogResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/** Ktor implementation of Mistral model discovery using the approved public API endpoint. */
public class MistralModelCatalog private constructor(
    private val credentials: MistralCredentialProvider,
    private val client: HttpClient,
) : ModelCatalogPort {
    override suspend fun listModels(): ModelCatalogResult {
        val apiKey = credentials.readSafeApiKey() ?: return failure(ModelCatalogFailureKind.CREDENTIAL_UNAVAILABLE)
        return requestModels(apiKey)
    }

    /** Releases network resources owned by this adapter. */
    public fun close(): Unit = client.close()

    private suspend fun requestModels(apiKey: String): ModelCatalogResult = try {
        val response = client.get(MISTRAL_MODELS_ENDPOINT) {
            bearerAuth(apiKey)
            accept(ContentType.Application.Json)
        }
        if (response.status.isSuccess()) response.toModelCatalogResult() else response.toFailure()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: HttpRequestTimeoutException) {
        failure(ModelCatalogFailureKind.UNAVAILABLE)
    } catch (_: IOException) {
        failure(ModelCatalogFailureKind.UNAVAILABLE)
    } catch (_: Exception) {
        failure(ModelCatalogFailureKind.UNEXPECTED)
    }

    private suspend fun io.ktor.client.statement.HttpResponse.toModelCatalogResult(): ModelCatalogResult = try {
        val models = body<MistralModelsResponseDto>().toDomainModels()
            ?: return failure(ModelCatalogFailureKind.INVALID_RESPONSE)
        ModelCatalogResult.Success(models)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        failure(ModelCatalogFailureKind.INVALID_RESPONSE)
    }

    /** Creates model-catalog adapters with the production HTTP policy. */
    public companion object {
        /** Creates an adapter with a platform-default Ktor engine and hardened HTTP policy. */
        public fun create(credentials: MistralCredentialProvider): MistralModelCatalog =
            MistralModelCatalog(credentials, createPlatformMistralHttpClient())

        internal fun create(credentials: MistralCredentialProvider, client: HttpClient): MistralModelCatalog =
            MistralModelCatalog(credentials, client)
    }
}
private const val MISTRAL_MODELS_ENDPOINT = "https://api.mistral.ai/v1/models"
