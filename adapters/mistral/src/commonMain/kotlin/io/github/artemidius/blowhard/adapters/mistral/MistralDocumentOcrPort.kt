package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.domain.DocumentOcrFailureKind
import io.github.artemidius.blowhard.core.domain.DocumentOcrPage
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** Mistral OCR adapter that sends verified local bytes as an in-memory data URI. */
public class MistralDocumentOcrPort private constructor(
    private val credentials: MistralCredentialProvider,
    private val client: HttpClient,
) : DocumentOcrPort {
    override suspend fun process(request: DocumentOcrRequest): DocumentOcrPortResult {
        val apiKey = credentials.readSafeApiKey()
            ?: return DocumentOcrPortResult.Failed(DocumentOcrFailureKind.AUTHENTICATION)
        return requestSafely(request, apiKey)
    }

    /** Releases network resources owned by this adapter. */
    public fun close(): Unit = client.close()

    private suspend fun requestSafely(request: DocumentOcrRequest, apiKey: String): DocumentOcrPortResult = try {
        val response = client.post(MISTRAL_OCR_ENDPOINT) {
            bearerAuth(apiKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = OCR_REQUEST_TIMEOUT_MILLIS }
            setBody(request.toDto())
        }
        if (response.status.isSuccess()) response.toResult() else response.toOcrFailure()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: HttpRequestTimeoutException) {
        DocumentOcrPortResult.Failed(DocumentOcrFailureKind.REMOTE_FAILURE)
    } catch (_: IOException) {
        DocumentOcrPortResult.Failed(DocumentOcrFailureKind.REMOTE_FAILURE)
    } catch (_: Exception) {
        DocumentOcrPortResult.Failed(DocumentOcrFailureKind.REMOTE_FAILURE)
    }

    private suspend fun HttpResponse.toResult(): DocumentOcrPortResult = try {
        if (contentType()?.match(ContentType.Application.Json) != true) return invalidResponse()
        val bytes = bodyAsChannel().readBuffer(MAX_RESPONSE_BYTES + 1L).readByteArray()
        if (bytes.size > MAX_RESPONSE_BYTES) return invalidResponse()
        val response = OCR_JSON.decodeFromString<MistralOcrResponseDto>(bytes.decodeToString())
        DocumentOcrPortResult.Processed(
            processor = PROCESSOR,
            pages = response.pages.map { DocumentOcrPage(it.index, it.markdown) },
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        invalidResponse()
    }

    /** Creates OCR adapters with the production HTTP policy. */
    public companion object {
        /** Creates an adapter with a platform-default Ktor engine and hardened HTTP policy. */
        public fun create(credentials: MistralCredentialProvider): MistralDocumentOcrPort =
            MistralDocumentOcrPort(credentials, createPlatformMistralHttpClient())

        internal fun create(credentials: MistralCredentialProvider, client: HttpClient): MistralDocumentOcrPort =
            MistralDocumentOcrPort(credentials, client)
    }
}

private fun DocumentOcrRequest.toDto(): MistralOcrRequestDto {
    val encoded = Base64.Default.encode(copyContent())
    val document = when (val descriptor = document.content) {
        is DocumentContentDescriptor.Pdf -> MistralOcrDocumentDto(
            type = "document_url",
            documentUrl = "data:application/pdf;base64,$encoded",
        )

        is DocumentContentDescriptor.Image -> MistralOcrDocumentDto(
            type = "image_url",
            imageUrl = "data:${descriptor.format.mimeType()};base64,$encoded",
        )
    }
    return MistralOcrRequestDto(
        model = PROCESSOR,
        document = document,
        includeImageBase64 = false,
        tableFormat = "markdown",
    )
}

private fun DocumentFormat.mimeType(): String = when (this) {
    DocumentFormat.PNG -> "image/png"
    DocumentFormat.JPEG -> "image/jpeg"
    DocumentFormat.PDF -> error("PDF is encoded as a document URL")
}

private fun HttpResponse.toOcrFailure(): DocumentOcrPortResult.Failed = DocumentOcrPortResult.Failed(
    when {
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
            DocumentOcrFailureKind.AUTHENTICATION

        status == HttpStatusCode.TooManyRequests -> DocumentOcrFailureKind.RATE_LIMITED

        status.value in CLIENT_ERROR_MINIMUM..CLIENT_ERROR_MAXIMUM -> DocumentOcrFailureKind.INPUT_REJECTED

        else -> DocumentOcrFailureKind.REMOTE_FAILURE
    },
)

private fun invalidResponse(): DocumentOcrPortResult.Failed =
    DocumentOcrPortResult.Failed(DocumentOcrFailureKind.INVALID_RESPONSE)

private const val MISTRAL_OCR_ENDPOINT = "https://api.mistral.ai/v1/ocr"
private const val PROCESSOR = "mistral-ocr-latest"
private const val OCR_REQUEST_TIMEOUT_MILLIS = 60_000L
private const val MAX_RESPONSE_BYTES = 4 * 1_024 * 1_024
private const val CLIENT_ERROR_MINIMUM = 400
private const val CLIENT_ERROR_MAXIMUM = 499
private val OCR_JSON = Json { ignoreUnknownKeys = true }
