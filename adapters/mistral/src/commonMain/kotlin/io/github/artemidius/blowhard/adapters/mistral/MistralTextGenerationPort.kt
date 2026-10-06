package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.TokenUsage
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.GenerationRetryAdvice
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.charsets.TooLongLineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

/** Streaming Mistral chat adapter for the core-owned text-generation boundary. */
public class MistralTextGenerationPort private constructor(
    private val credentials: MistralCredentialProvider,
    private val configuration: MistralChatConfiguration,
    private val client: HttpClient,
) : TextGenerationPort {
    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = channelFlow {
        if (request.prompt.value.length > MAX_PROMPT_CHARACTERS) {
            emitFailure(GenerationFailure(GenerationFailureKind.INVALID_REQUEST))
            return@channelFlow
        }
        val apiKey = credentials.readSafeApiKey()
        if (apiKey == null) {
            emitFailure(GenerationFailure(GenerationFailureKind.CREDENTIAL_UNAVAILABLE))
            return@channelFlow
        }
        stream(request, apiKey)
    }

    /** Releases network resources owned by this adapter. */
    public fun close(): Unit = client.close()

    private suspend fun SendChannel<GenerationEvent>.stream(request: GenerationRequest, apiKey: String) {
        val batcher = TextDeltaBatcher { send(it) }
        try {
            executeRequest(request, apiKey, batcher)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: HttpRequestTimeoutException) {
            batcher.flush()
            emitFailure(unavailableFailure())
        } catch (_: TooLongLineException) {
            batcher.flush()
            emitFailure(invalidResponseFailure())
        } catch (_: IOException) {
            batcher.flush()
            emitFailure(unavailableFailure())
        } catch (_: InvalidMistralStreamException) {
            batcher.flush()
            emitFailure(invalidResponseFailure())
        } catch (_: Exception) {
            batcher.flush()
            emitFailure(unexpectedFailure())
        }
    }

    private suspend fun SendChannel<GenerationEvent>.executeRequest(
        request: GenerationRequest,
        apiKey: String,
        batcher: TextDeltaBatcher,
    ) {
        client.preparePost(MISTRAL_CHAT_ENDPOINT) {
            bearerAuth(apiKey)
            accept(ContentType.Text.EventStream)
            contentType(ContentType.Application.Json)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = STREAM_SOCKET_TIMEOUT_MILLIS
            }
            setBody(request.toDto())
        }.execute { response ->
            when {
                !response.status.isSuccess() -> emitFailure(response.toGenerationFailure())

                response.contentType()?.match(ContentType.Text.EventStream) != true ->
                    emitFailure(invalidResponseFailure())

                else -> consumeSuccessfulResponse(response.bodyAsChannel(), batcher)
            }
        }
    }

    private suspend fun SendChannel<GenerationEvent>.consumeSuccessfulResponse(
        channel: io.ktor.utils.io.ByteReadChannel,
        batcher: TextDeltaBatcher,
    ) {
        var sawChoice = false
        var finishObserved = false
        var reportedUsage: TokenUsage? = null
        val result = channel.readMistralSse { data ->
            val chunk = try {
                STREAM_JSON.decodeFromString<MistralChatChunkDto>(data).toParsedChunk()
            } catch (_: Exception) {
                null
            } ?: throw InvalidMistralStreamException()
            if (finishObserved && chunk.text.isNotEmpty()) throw InvalidMistralStreamException()
            sawChoice = sawChoice || chunk.hasChoice
            finishObserved = finishObserved || chunk.finished
            reportedUsage = reportedUsage.merge(chunk.usage)
            batcher.add(chunk.text)
            true
        }
        batcher.flush()
        when {
            result == SseReadResult.DONE && sawChoice -> {
                reportedUsage?.let { send(GenerationEvent.Usage(it)) }
                send(GenerationEvent.Completed)
            }

            result == SseReadResult.DONE -> emitFailure(invalidResponseFailure())

            else -> emitFailure(unavailableFailure())
        }
    }

    private fun GenerationRequest.toDto(): MistralChatRequestDto = MistralChatRequestDto(
        model = configuration.modelId,
        messages = listOf(MistralChatMessageDto(role = "user", content = prompt.value)),
        stream = true,
        streamOptions = MistralStreamOptionsDto(includeUsage = true),
        maxTokens = parameters.maxOutputTokens,
        temperature = parameters.temperature,
    )

    /** Creates streaming adapters with the production HTTP policy. */
    public companion object {
        /** Creates an adapter with a platform-default Ktor engine and validated provider settings. */
        public fun create(
            credentials: MistralCredentialProvider,
            configuration: MistralChatConfiguration,
        ): MistralTextGenerationPort =
            MistralTextGenerationPort(credentials, configuration, createPlatformMistralHttpClient())

        internal fun create(
            credentials: MistralCredentialProvider,
            configuration: MistralChatConfiguration,
            client: HttpClient,
        ): MistralTextGenerationPort = MistralTextGenerationPort(credentials, configuration, client)
    }
}

private fun TokenUsage?.merge(update: TokenUsage?): TokenUsage? = when {
    this == null -> update

    update == null -> this

    else -> TokenUsage(
        inputTokens = inputTokens.mergeCount(update.inputTokens),
        outputTokens = outputTokens.mergeCount(update.outputTokens),
        totalTokens = totalTokens.mergeCount(update.totalTokens),
    )
}

private fun Long?.mergeCount(update: Long?): Long? {
    if (this != null && update != null && this != update) throw InvalidMistralStreamException()
    return update ?: this
}

private suspend fun SendChannel<GenerationEvent>.emitFailure(failure: GenerationFailure) {
    send(GenerationEvent.Failed(failure))
}

private fun invalidResponseFailure(): GenerationFailure = GenerationFailure(
    GenerationFailureKind.INVALID_RESPONSE,
    retryAdvice = GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT,
)

private fun unavailableFailure(): GenerationFailure = GenerationFailure(
    GenerationFailureKind.UNAVAILABLE,
    retryAdvice = GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT,
)

private fun unexpectedFailure(): GenerationFailure = GenerationFailure(GenerationFailureKind.UNEXPECTED)

private val STREAM_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

private const val MAX_PROMPT_CHARACTERS = 131_072
private const val STREAM_SOCKET_TIMEOUT_MILLIS = 30_000L
private const val MISTRAL_CHAT_ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
