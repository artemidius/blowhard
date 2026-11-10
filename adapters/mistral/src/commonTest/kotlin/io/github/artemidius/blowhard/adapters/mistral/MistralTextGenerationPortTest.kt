package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.TokenUsage
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.GenerationRetryAdvice
import io.github.artemidius.blowhard.core.domain.PromptText
import io.github.artemidius.blowhard.core.domain.PromptTextResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MistralTextGenerationPortTest {
    @Test
    fun `request contains only documented chat fields and streams text`() = runTest {
        val fixture = generationResponding(successStream("Hello"))
        try {
            assertEquals(
                listOf(GenerationEvent.TextDelta("Hello"), GenerationEvent.Completed),
                fixture.port.generate(request("private prompt")).toList(),
            )

            val sent = fixture.requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("https://api.mistral.ai/v1/chat/completions", sent.url.toString())
            assertEquals("Bearer synthetic-token", sent.headers[HttpHeaders.Authorization])
            assertEquals(ContentType.Application.Json, sent.body.contentType)
            assertEquals(ContentType.Text.EventStream.toString(), sent.headers[HttpHeaders.Accept])
            val json = Json.parseToJsonElement(sent.body.toByteArray().decodeToString()).jsonObject
            assertEquals("mistral-small-latest", json.getValue("model").jsonPrimitive.content)
            assertTrue(json.getValue("stream").jsonPrimitive.boolean)
            assertTrue(
                json.getValue("stream_options").jsonObject.getValue("include_usage").jsonPrimitive.boolean,
            )
            assertEquals(64, json.getValue("max_tokens").jsonPrimitive.int)
            assertEquals(0.2, json.getValue("temperature").jsonPrimitive.double)
            val message = json.getValue("messages").jsonArray.single().jsonObject
            assertEquals("user", message.getValue("role").jsonPrimitive.content)
            assertEquals("private prompt", message.getValue("content").jsonPrimitive.content)
            assertFalse("runId" in json)
            assertFalse("conversationId" in json)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `model defaults omit optional generation controls`() = runTest {
        val fixture = generationResponding(successStream("Hello"))
        try {
            fixture.port.generate(request(parameters = GenerationParameters())).toList()

            val sent = fixture.requests.single()
            val json = Json.parseToJsonElement(sent.body.toByteArray().decodeToString()).jsonObject
            assertFalse("max_tokens" in json)
            assertFalse("temperature" in json)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `text arrays and compatible partial usage frames are accepted`() = runTest {
        val body = sse(
            chatChunk(content = "[{\"type\":\"text\",\"text\":\"Hello \"}]"),
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":3}}",
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":3}}",
            "{\"choices\":[],\"usage\":{\"completion_tokens\":2,\"total_tokens\":5}}",
            chatChunk(content = "\"world\"", finishReason = "stop"),
            "[DONE]",
        )
        val fixture = generationResponding(body)
        try {
            assertEquals(
                listOf(
                    GenerationEvent.TextDelta("Hello world"),
                    GenerationEvent.Usage(TokenUsage(inputTokens = 3, outputTokens = 2, totalTokens = 5)),
                    GenerationEvent.Completed,
                ),
                fixture.port.generate(request()).toList(),
            )
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `conflicting usage frames invalidate a successful stream`() = runTest {
        val fixture = generationResponding(
            sse(
                chatChunk(content = "\"partial\""),
                "{\"choices\":[],\"usage\":{\"completion_tokens\":2}}",
                "{\"choices\":[],\"usage\":{\"completion_tokens\":3}}",
                "[DONE]",
            ),
        )
        try {
            val events = fixture.port.generate(request()).toList()

            assertEquals("partial", assertIs<GenerationEvent.TextDelta>(events.first()).text)
            assertEquals(
                GenerationFailureKind.INVALID_RESPONSE,
                assertIs<GenerationEvent.Failed>(events.last()).failure.kind,
            )
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `small provider tokens are coalesced and no data is accepted after done`() = runTest {
        val tokens = List(130) { chatChunk(content = "\"x\"") }
        val body = sse(*(tokens + "[DONE]" + chatChunk(content = "\"late\"")).toTypedArray())
        val fixture = generationResponding(body)
        try {
            val events = fixture.port.generate(request()).toList()
            assertEquals(3, events.size)
            assertEquals("x".repeat(128), assertIs<GenerationEvent.TextDelta>(events[0]).text)
            assertEquals("xx", assertIs<GenerationEvent.TextDelta>(events[1]).text)
            assertEquals(GenerationEvent.Completed, events[2])
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `partial output is preserved when stream ends without done`() = runTest {
        val fixture = generationResponding(sse(chatChunk(content = "\"partial\"")))
        try {
            val events = fixture.port.generate(request()).toList()
            assertEquals("partial", assertIs<GenerationEvent.TextDelta>(events[0]).text)
            val failure = assertIs<GenerationEvent.Failed>(events[1]).failure
            assertEquals(GenerationFailureKind.UNAVAILABLE, failure.kind)
            assertEquals(GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT, failure.retryAdvice)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `text after a finish reason preserves partial output and rejects the stream`() = runTest {
        val fixture = generationResponding(
            sse(
                chatChunk(content = "\"partial\"", finishReason = "stop"),
                chatChunk(content = "\"late\""),
                "[DONE]",
            ),
        )
        try {
            val events = fixture.port.generate(request()).toList()
            assertEquals("partial", assertIs<GenerationEvent.TextDelta>(events[0]).text)
            assertEquals(
                GenerationFailureKind.INVALID_RESPONSE,
                assertIs<GenerationEvent.Failed>(events[1]).failure.kind,
            )
        } finally {
            fixture.port.close()
        }
    }

    @Test
    fun `malformed successful streams fail safely`() = runTest {
        val bodies = listOf(
            sse("not-json", "[DONE]"),
            sse("{\"choices\":[]}", "[DONE]"),
            sse(chatChunk(index = 1), "[DONE]"),
            sse(chatChunk(content = "{\"type\":\"image\"}"), "[DONE]"),
            sse(chatChunk(content = "[\"not-an-object\"]"), "[DONE]"),
            sse(chatChunk(content = "[{\"text\":\"missing-type\"}]"), "[DONE]"),
            sse(chatChunk(content = "[{\"type\":\"image\",\"text\":\"x\"}]"), "[DONE]"),
            sse(chatChunk(content = "[{\"type\":\"text\",\"text\":17}]"), "[DONE]"),
            sse("[DONE]"),
        )
        bodies.forEach { body ->
            val fixture = generationResponding(body)
            try {
                val failure = assertIs<GenerationEvent.Failed>(fixture.port.generate(request()).toList().single())
                assertEquals(GenerationFailureKind.INVALID_RESPONSE, failure.failure.kind)
                assertEquals(GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT, failure.failure.retryAdvice)
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    fun `wrong media type and redirect are rejected without following`() = runTest {
        val wrongType = generationResponding("{}", contentType = ContentType.Application.Json)
        val redirect = generationResponding(
            body = "",
            status = HttpStatusCode.Found,
            headers = headersOf(HttpHeaders.Location, "https://untrusted.example/chat"),
        )
        try {
            assertFailure(GenerationFailureKind.INVALID_RESPONSE, wrongType.port.generate(request()).toList())
            assertFailure(GenerationFailureKind.INVALID_RESPONSE, redirect.port.generate(request()).toList())
            assertEquals(1, redirect.requests.size)
            assertEquals("api.mistral.ai", redirect.requests.single().url.host)
        } finally {
            wrongType.port.close()
            redirect.port.close()
        }
    }

    @Test
    fun `HTTP failures have conservative retry advice and never retain response body`() = runTest {
        val cases = listOf(
            FailureCase(HttpStatusCode.Unauthorized, GenerationFailureKind.UNAUTHORIZED),
            FailureCase(HttpStatusCode.Forbidden, GenerationFailureKind.UNAUTHORIZED),
            FailureCase(HttpStatusCode.UnprocessableEntity, GenerationFailureKind.INVALID_REQUEST),
            FailureCase(
                HttpStatusCode.Found,
                GenerationFailureKind.INVALID_RESPONSE,
            ),
            FailureCase(
                HttpStatusCode.TooManyRequests,
                GenerationFailureKind.RATE_LIMITED,
                GenerationRetryAdvice.RETRY_AFTER_DELAY,
                retryAfterHeader = "7",
                expectedRetryAfter = 7,
            ),
            FailureCase(
                HttpStatusCode.RequestTimeout,
                GenerationFailureKind.UNAVAILABLE,
                GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT,
            ),
            FailureCase(
                HttpStatusCode.ServiceUnavailable,
                GenerationFailureKind.UNAVAILABLE,
                GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT,
                retryAfterHeader = "30",
            ),
        )
        cases.forEach { assertFailureCase(it) }
    }

    @Test
    fun `invalid and missing retry delay require an explicit new attempt`() = runTest {
        val retryValues = listOf(null, "tomorrow", "-1", "999999")
        retryValues.forEach { retryValue ->
            val headers = if (retryValue == null) {
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            } else {
                headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.RetryAfter to listOf(retryValue),
                )
            }
            val fixture = generationResponding("{}", HttpStatusCode.TooManyRequests, headers)
            try {
                val failure = assertIs<GenerationEvent.Failed>(
                    fixture.port.generate(request()).toList().single(),
                ).failure
                assertEquals(GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT, failure.retryAdvice)
                assertNull(failure.retryAfterSeconds)
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    fun `missing unsafe and failed credentials do not start a request`() = runTest {
        val providers = listOf<MistralCredentialProvider>(
            MistralCredentialProvider { null },
            MistralCredentialProvider { "contains whitespace" },
            MistralCredentialProvider { error("private credential detail") },
        )
        providers.forEach { credentials ->
            var requests = 0
            val client = createMistralHttpClient(
                MockEngine {
                    requests += 1
                    respond(successStream("unexpected"))
                },
            )
            val port = MistralTextGenerationPort.create(credentials, CONFIGURATION, client)
            try {
                assertFailure(GenerationFailureKind.CREDENTIAL_UNAVAILABLE, port.generate(request()).toList())
                assertEquals(0, requests)
            } finally {
                port.close()
            }
        }
    }

    @Test
    fun `oversized prompt is rejected before credentials or network are accessed`() = runTest {
        var credentialReads = 0
        var requests = 0
        val client = createMistralHttpClient(
            MockEngine {
                requests += 1
                respond(successStream("unexpected"))
            },
        )
        val port = MistralTextGenerationPort.create(
            MistralCredentialProvider {
                credentialReads += 1
                "synthetic-token"
            },
            CONFIGURATION,
            client,
        )
        try {
            assertFailure(
                GenerationFailureKind.INVALID_REQUEST,
                port.generate(request("x".repeat(131_073))).toList(),
            )
            assertEquals(0, credentialReads)
            assertEquals(0, requests)
        } finally {
            port.close()
        }
    }

    @Test
    fun `network interruption is redacted and is not retried automatically`() = runTest {
        var requests = 0
        val client = createMistralHttpClient(
            MockEngine {
                requests += 1
                throw IOException("private network detail")
            },
        )
        val port = MistralTextGenerationPort.create(validCredentials, CONFIGURATION, client)
        try {
            val events = port.generate(request()).toList()
            val failure = assertIs<GenerationEvent.Failed>(events.single()).failure
            assertEquals(GenerationFailureKind.UNAVAILABLE, failure.kind)
            assertEquals(GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT, failure.retryAdvice)
            assertNull(failure.reference)
            assertEquals(1, requests)
        } finally {
            port.close()
        }
    }

    @Test
    fun `unexpected transport failure is redacted and not retryable`() = runTest {
        val client = createMistralHttpClient(MockEngine { error("private implementation detail") })
        val port = MistralTextGenerationPort.create(validCredentials, CONFIGURATION, client)
        try {
            val failure = assertIs<GenerationEvent.Failed>(port.generate(request()).toList().single()).failure
            assertEquals(GenerationFailureKind.UNEXPECTED, failure.kind)
            assertEquals(GenerationRetryAdvice.DO_NOT_RETRY, failure.retryAdvice)
            assertNull(failure.reference)
        } finally {
            port.close()
        }
    }

    @Test
    fun `cancelling collection cancels the in-flight provider request`() = runTest(timeout = 10.seconds) {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val client = createMistralHttpClient(
            MockEngine {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            },
        )
        val port = MistralTextGenerationPort.create(validCredentials, CONFIGURATION, client)
        try {
            val collection = backgroundScope.launch { port.generate(request()).toList() }
            started.await()
            collection.cancelAndJoin()
            cancelled.await()
        } finally {
            port.close()
        }
    }
}

private data class FailureCase(
    val status: HttpStatusCode,
    val kind: GenerationFailureKind,
    val advice: GenerationRetryAdvice = GenerationRetryAdvice.DO_NOT_RETRY,
    val retryAfterHeader: String? = null,
    val expectedRetryAfter: Long? = null,
)

private data class GenerationFixture(val port: MistralTextGenerationPort, val requests: List<HttpRequestData>)

private suspend fun assertFailureCase(case: FailureCase) {
    val headers = if (case.retryAfterHeader == null) {
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    } else {
        headersOf(
            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
            HttpHeaders.RetryAfter to listOf(case.retryAfterHeader),
        )
    }
    val fixture = generationResponding("{\"message\":\"private provider detail\"}", case.status, headers)
    try {
        val failure = assertIs<GenerationEvent.Failed>(fixture.port.generate(request()).toList().single()).failure
        assertEquals(case.kind, failure.kind)
        assertEquals(case.advice, failure.retryAdvice)
        assertEquals(case.expectedRetryAfter, failure.retryAfterSeconds)
        assertNull(failure.reference)
        assertEquals(1, fixture.requests.size)
    } finally {
        fixture.port.close()
    }
}

private fun generationResponding(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: io.ktor.http.Headers? = null,
    contentType: ContentType = ContentType.Text.EventStream,
): GenerationFixture {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
        requests += request
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headers ?: headersOf(HttpHeaders.ContentType, contentType.toString()),
        )
    }
    return GenerationFixture(
        MistralTextGenerationPort.create(validCredentials, CONFIGURATION, createMistralHttpClient(engine)),
        requests,
    )
}

private fun assertFailure(expected: GenerationFailureKind, events: List<GenerationEvent>) {
    assertEquals(expected, assertIs<GenerationEvent.Failed>(events.single()).failure.kind)
}

private fun request(
    prompt: String = "hello",
    parameters: GenerationParameters = GenerationParameters(maxOutputTokens = 64, temperature = 0.2),
): GenerationRequest {
    val accepted = assertIs<PromptTextResult.Accepted>(PromptText.from(prompt))
    return GenerationRequest(
        RunId("run-1"),
        ConversationId("conversation-1"),
        accepted.prompt,
        parameters = parameters,
    )
}

private fun successStream(text: String): String = sse(
    chatChunk(content = "\"$text\"", finishReason = "stop"),
    "[DONE]",
)

private fun chatChunk(content: String = "null", finishReason: String? = null, index: Int = 0): String {
    val finish = finishReason?.let { "\"$it\"" } ?: "null"
    return """{"choices":[{"index":$index,"delta":{"content":$content},"finish_reason":$finish}]}"""
}

private fun sse(vararg data: String): String = data.joinToString(separator = "") { "data: $it\r\n\r\n" }

private val validCredentials = MistralCredentialProvider { "synthetic-token" }
private val CONFIGURATION = MistralChatConfiguration("mistral-small-latest")
