package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Fixed-host Mistral adapter for strict, non-streaming meeting-note extraction. */
public class MistralMeetingNoteGenerationPort private constructor(
    private val credentials: MistralCredentialProvider,
    private val configuration: MistralChatConfiguration,
    private val client: HttpClient,
) : MeetingNoteGenerationPort {
    override suspend fun generate(request: MeetingNoteGenerationRequest): MeetingNoteGenerationResult {
        val apiKey = credentials.readSafeApiKey()
        return when {
            apiKey == null -> failed(MeetingNoteGenerationFailureKind.AUTHENTICATION)

            else -> request.toDto(configuration.modelId)?.let { requestSafely(it, apiKey) }
                ?: failed(MeetingNoteGenerationFailureKind.INPUT_REJECTED)
        }
    }

    /** Releases network resources owned by this adapter. */
    public fun close(): Unit = client.close()

    private suspend fun requestSafely(
        body: MistralMeetingNoteRequestDto,
        apiKey: String,
    ): MeetingNoteGenerationResult = try {
        val response = client.post(MISTRAL_CHAT_ENDPOINT) {
            bearerAuth(apiKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = STRUCTURED_REQUEST_TIMEOUT_MILLIS }
            setBody(body)
        }
        if (response.status.isSuccess()) response.toMeetingNoteResult() else response.toMeetingNoteFailure()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: HttpRequestTimeoutException) {
        failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE)
    } catch (_: IOException) {
        failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE)
    } catch (_: Exception) {
        failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE)
    }

    private suspend fun HttpResponse.toMeetingNoteResult(): MeetingNoteGenerationResult = try {
        val validContentType = contentType()?.match(ContentType.Application.Json) == true
        val bytes = bodyAsChannel().readBuffer(MAX_RESPONSE_BYTES + 1L).readByteArray()
        val result = bytes.takeIf { validContentType && it.size <= MAX_RESPONSE_BYTES }
            ?.decodeToString()
            ?.let { RESPONSE_JSON.decodeFromString<MistralMeetingNoteResponseDto>(it) }
            ?.toGeneratedResult()
        result ?: failed(MeetingNoteGenerationFailureKind.INVALID_RESPONSE)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        failed(MeetingNoteGenerationFailureKind.INVALID_RESPONSE)
    }

    /** Creates structured-output adapters with the production HTTP policy. */
    public companion object {
        /** Creates an adapter with a platform-default Ktor engine and hardened HTTP policy. */
        public fun create(
            credentials: MistralCredentialProvider,
            configuration: MistralChatConfiguration,
        ): MistralMeetingNoteGenerationPort = MistralMeetingNoteGenerationPort(
            credentials,
            configuration,
            createPlatformMistralHttpClient(),
        )

        internal fun create(
            credentials: MistralCredentialProvider,
            configuration: MistralChatConfiguration,
            client: HttpClient,
        ): MistralMeetingNoteGenerationPort = MistralMeetingNoteGenerationPort(credentials, configuration, client)
    }
}

private fun MeetingNoteGenerationRequest.toDto(modelId: String): MistralMeetingNoteRequestDto? {
    val schema = try {
        REQUEST_JSON.parseToJsonElement(schemaJson) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    return MistralMeetingNoteRequestDto(
        model = modelId,
        messages = listOf(
            MistralMeetingNoteMessageDto("system", instructions),
            MistralMeetingNoteMessageDto("user", sourceJson),
        ),
        responseFormat = MistralStructuredResponseFormatDto(
            type = "json_schema",
            jsonSchema = MistralJsonSchemaDto(schemaName, schema, strict = true),
        ),
        maxTokens = maximumOutputTokens,
        temperature = 0.0,
        stream = false,
    )
}

private fun MistralMeetingNoteResponseDto.toGeneratedResult(): MeetingNoteGenerationResult.Generated? {
    val choice = choices.singleOrNull()?.takeIf { it.index == 0 && it.finishReason == "stop" }
    val content = (choice?.message?.content as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
        ?.takeIf { it.length <= MAX_OUTPUT_CHARACTERS }
    val reportedModel = model?.takeIf { it.isSafeModelId() }
    return if (content != null && reportedModel != null) {
        MeetingNoteGenerationResult.Generated(content, reportedModel)
    } else {
        null
    }
}

private fun String.isSafeModelId(): Boolean =
    isNotBlank() && length <= MAX_MODEL_ID_LENGTH && none { it.isWhitespace() || it.isISOControl() }

private fun HttpResponse.toMeetingNoteFailure(): MeetingNoteGenerationResult.Failed = failed(
    when {
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
            MeetingNoteGenerationFailureKind.AUTHENTICATION

        status == HttpStatusCode.TooManyRequests -> MeetingNoteGenerationFailureKind.RATE_LIMITED

        status.value in CLIENT_ERROR_MINIMUM..CLIENT_ERROR_MAXIMUM -> MeetingNoteGenerationFailureKind.INPUT_REJECTED

        else -> MeetingNoteGenerationFailureKind.REMOTE_FAILURE
    },
)

private fun failed(kind: MeetingNoteGenerationFailureKind): MeetingNoteGenerationResult.Failed =
    MeetingNoteGenerationResult.Failed(kind)

private const val MISTRAL_CHAT_ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
private const val STRUCTURED_REQUEST_TIMEOUT_MILLIS = 60_000L
private const val MAX_RESPONSE_BYTES = 512 * 1_024
private const val MAX_OUTPUT_CHARACTERS = 128_000
private const val MAX_MODEL_ID_LENGTH = 256
private const val CLIENT_ERROR_MINIMUM = 400
private const val CLIENT_ERROR_MAXIMUM = 499
private val REQUEST_JSON = Json
private val RESPONSE_JSON = Json { ignoreUnknownKeys = true }
