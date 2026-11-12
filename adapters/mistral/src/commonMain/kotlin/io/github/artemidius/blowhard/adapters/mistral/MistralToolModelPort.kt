package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolModelFailureKind
import io.github.artemidius.blowhard.core.domain.ToolModelMessage
import io.github.artemidius.blowhard.core.domain.ToolModelPort
import io.github.artemidius.blowhard.core.domain.ToolModelRequest
import io.github.artemidius.blowhard.core.domain.ToolModelResponse
import io.github.artemidius.blowhard.core.domain.ToolName
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Fixed-host Mistral adapter for complete, non-streaming tool turns. */
public class MistralToolModelPort private constructor(
    private val credentials: MistralCredentialProvider,
    private val configuration: MistralChatConfiguration,
    private val client: HttpClient,
) : ToolModelPort {
    override suspend fun next(request: ToolModelRequest): ToolModelResponse {
        val apiKey = credentials.readSafeApiKey()
        return when {
            apiKey == null -> failed(ToolModelFailureKind.AUTHENTICATION)

            else -> request.toDto(configuration.modelId)?.let { requestSafely(it, apiKey) }
                ?: failed(ToolModelFailureKind.INVALID_RESPONSE)
        }
    }

    /** Releases network resources owned by this adapter. */
    public fun close(): Unit = client.close()

    private suspend fun requestSafely(body: MistralToolRequestDto, apiKey: String): ToolModelResponse = try {
        val response = client.post(MISTRAL_TOOL_CHAT_ENDPOINT) {
            bearerAuth(apiKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = TOOL_REQUEST_TIMEOUT_MILLIS }
            setBody(body)
        }
        if (response.status.isSuccess()) response.toToolResponse() else response.toToolFailure()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: HttpRequestTimeoutException) {
        failed(ToolModelFailureKind.UNAVAILABLE)
    } catch (_: IOException) {
        failed(ToolModelFailureKind.UNAVAILABLE)
    } catch (_: Exception) {
        failed(ToolModelFailureKind.UNAVAILABLE)
    }

    private suspend fun HttpResponse.toToolResponse(): ToolModelResponse = try {
        val validContentType = contentType()?.match(ContentType.Application.Json) == true
        val bytes = bodyAsChannel().readBuffer(MAX_TOOL_RESPONSE_BYTES + 1L).readByteArray()
        bytes.takeIf { validContentType && it.size <= MAX_TOOL_RESPONSE_BYTES }
            ?.decodeToString()
            ?.let { TOOL_JSON.decodeFromString<MistralToolResponseDto>(it) }
            ?.toCoreResponse()
            ?: failed(ToolModelFailureKind.INVALID_RESPONSE)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        failed(ToolModelFailureKind.INVALID_RESPONSE)
    }

    /** Creates tool adapters with the production HTTP policy. */
    public companion object {
        /** Creates an adapter with a platform-default Ktor engine and hardened HTTP policy. */
        public fun create(
            credentials: MistralCredentialProvider,
            configuration: MistralChatConfiguration,
        ): MistralToolModelPort = MistralToolModelPort(credentials, configuration, createPlatformMistralHttpClient())

        internal fun create(
            credentials: MistralCredentialProvider,
            configuration: MistralChatConfiguration,
            client: HttpClient,
        ): MistralToolModelPort = MistralToolModelPort(credentials, configuration, client)
    }
}

private fun ToolModelRequest.toDto(modelId: String): MistralToolRequestDto? = runCatching {
    MistralToolRequestDto(
        model = modelId,
        messages = messages.flatMap(ToolModelMessage::toDtos),
        tools = definitions.map(ToolDefinition::toDto),
        toolChoice = "auto",
        maxTokens = MAX_TOOL_TURN_TOKENS,
        temperature = 0.0,
        stream = false,
    )
}.getOrNull()

private fun ToolDefinition.toDto(): MistralToolDto = MistralToolDto(
    "function",
    MistralFunctionDefinitionDto(
        name.value,
        description,
        requireNotNull(TOOL_JSON.parseToJsonElement(inputSchemaJson) as? JsonObject),
    ),
)

private fun ToolModelMessage.toDtos(): List<MistralToolMessageDto> = when (this) {
    is ToolModelMessage.User -> listOf(MistralToolMessageDto("user", JsonPrimitive(text)))

    is ToolModelMessage.AssistantCalls -> listOf(
        MistralToolMessageDto(
            role = "assistant",
            content = JsonNull,
            toolCalls = calls.map { call ->
                MistralToolCallDto(
                    call.id.value,
                    "function",
                    MistralFunctionCallDto(call.name.value, call.argumentsJson),
                )
            },
        ),
    )

    is ToolModelMessage.ToolResults -> results.map { result ->
        MistralToolMessageDto(
            role = "tool",
            content = JsonPrimitive(result.outputJson),
            toolCallId = result.id.value,
            name = result.name.value,
        )
    }
}

private fun MistralToolResponseDto.toCoreResponse(): ToolModelResponse? {
    val choice = choices.singleOrNull()?.takeIf { it.index == 0 } ?: return null
    val content = choice.message.content.stringOrNull()
    val calls = choice.message.toolCalls
    return when {
        choice.finishReason == "stop" && calls.isNullOrEmpty() && !content.isNullOrBlank() ->
            ToolModelResponse.Text(content)

        choice.finishReason == "tool_calls" && !calls.isNullOrEmpty() ->
            calls.toProposals()?.let { ToolModelResponse.Calls(it, content) }

        else -> null
    }
}

private fun List<MistralToolCallDto>.toProposals(): List<ToolCallProposal>? = mapNotNull { call ->
    runCatching {
        call.takeIf { it.type == "function" }?.let {
            val function = requireNotNull(it.function)
            ToolCallProposal(
                ToolCallId(requireNotNull(it.id)),
                ToolName(requireNotNull(function.name)),
                requireNotNull(function.arguments),
            )
        }
    }.getOrNull()
}.takeIf { it.size == size }

private fun kotlinx.serialization.json.JsonElement?.stringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull?.takeIf { isString }
    else -> null
}

private fun HttpResponse.toToolFailure(): ToolModelResponse.Failed = failed(
    when {
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
            ToolModelFailureKind.AUTHENTICATION

        status == HttpStatusCode.TooManyRequests -> ToolModelFailureKind.RATE_LIMITED

        status.value in TOOL_CLIENT_ERROR_MINIMUM..TOOL_CLIENT_ERROR_MAXIMUM -> ToolModelFailureKind.INVALID_RESPONSE

        else -> ToolModelFailureKind.UNAVAILABLE
    },
)

private fun failed(kind: ToolModelFailureKind): ToolModelResponse.Failed = ToolModelResponse.Failed(kind)

private const val MISTRAL_TOOL_CHAT_ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
private const val TOOL_REQUEST_TIMEOUT_MILLIS = 60_000L
private const val MAX_TOOL_RESPONSE_BYTES = 512 * 1_024
private const val MAX_TOOL_TURN_TOKENS = 4_096
private const val TOOL_CLIENT_ERROR_MINIMUM = 400
private const val TOOL_CLIENT_ERROR_MAXIMUM = 499
private val TOOL_JSON = Json { ignoreUnknownKeys = true }
