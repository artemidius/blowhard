package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

internal class MistralMeetingNoteGenerationPortTest {
    @Test
    internal fun `request sends strict schema with isolated instructions and source`() = runTest {
        val fixture = structuredResponding(SUCCESS_RESPONSE)
        try {
            val result = assertIs<MeetingNoteGenerationResult.Generated>(fixture.port.generate(request()))

            assertEquals("{\"template_version\":\"meeting-note-v1\"}", result.json)
            assertEquals("mistral-small-latest", result.modelId)
            val sent = fixture.requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("https://api.mistral.ai/v1/chat/completions", sent.url.toString())
            assertEquals("Bearer synthetic-token", sent.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(sent.body.toByteArray().decodeToString()).jsonObject
            assertEquals("configured-model", body.getValue("model").jsonPrimitive.content)
            assertFalse(body.getValue("stream").jsonPrimitive.boolean)
            assertEquals(0.0, body.getValue("temperature").jsonPrimitive.double)
            assertEquals(512, body.getValue("max_tokens").jsonPrimitive.int)
            val messages = body.getValue("messages").jsonArray
            assertEquals(
                listOf("system", "user"),
                messages.map {
                    it.jsonObject.getValue("role").jsonPrimitive.content
                },
            )
            assertEquals("extract safely", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
            assertEquals("{\"pages\":[]}", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
            val format = body.getValue("response_format").jsonObject
            assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
            val schema = format.getValue("json_schema").jsonObject
            assertEquals("note_v1", schema.getValue("name").jsonPrimitive.content)
            assertEquals("object", schema.getValue("schema").jsonObject.getValue("type").jsonPrimitive.content)
            assertEquals(true, schema.getValue("strict").jsonPrimitive.boolean)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    internal fun `missing credentials and invalid schema never reach the network`() = runTest {
        val unavailableCredentials = listOf(
            MistralCredentialProvider { null },
            MistralCredentialProvider { "contains whitespace" },
            MistralCredentialProvider { error("private credential detail") },
        )
        unavailableCredentials.forEach { credentials ->
            val fixture = structuredResponding(SUCCESS_RESPONSE, credentials = credentials)
            try {
                assertFailure(MeetingNoteGenerationFailureKind.AUTHENTICATION, fixture.port.generate(request()))
                assertEquals(0, fixture.requests.size)
            } finally {
                fixture.port.close()
            }
        }
        listOf("[]", "not-json").forEach { schema ->
            val fixture = structuredResponding(SUCCESS_RESPONSE)
            try {
                assertFailure(
                    MeetingNoteGenerationFailureKind.INPUT_REJECTED,
                    fixture.port.generate(request(schemaJson = schema)),
                )
                assertEquals(0, fixture.requests.size)
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    internal fun `HTTP failures are classified without exposing provider bodies`() = runTest {
        val cases = mapOf(
            HttpStatusCode.BadRequest to MeetingNoteGenerationFailureKind.INPUT_REJECTED,
            HttpStatusCode.PayloadTooLarge to MeetingNoteGenerationFailureKind.INPUT_REJECTED,
            HttpStatusCode.NotFound to MeetingNoteGenerationFailureKind.INPUT_REJECTED,
            HttpStatusCode.Unauthorized to MeetingNoteGenerationFailureKind.AUTHENTICATION,
            HttpStatusCode.Forbidden to MeetingNoteGenerationFailureKind.AUTHENTICATION,
            HttpStatusCode.TooManyRequests to MeetingNoteGenerationFailureKind.RATE_LIMITED,
            HttpStatusCode.InternalServerError to MeetingNoteGenerationFailureKind.REMOTE_FAILURE,
        )
        cases.forEach { (status, expected) ->
            val fixture = structuredResponding("""{"detail":"private provider body"}""", status)
            try {
                assertFailure(expected, fixture.port.generate(request()))
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    internal fun `malformed truncated and oversized responses fail closed`() = runTest {
        val responses = listOf(
            "not-json",
            "{}",
            SUCCESS_RESPONSE.replace("\"stop\"", "\"length\""),
            SUCCESS_RESPONSE.replace("\"mistral-small-latest\"", "\"unsafe model\""),
            SUCCESS_RESPONSE.replace("\"index\": 0", "\"index\": 1"),
            SUCCESS_RESPONSE.replace(
                "\"content\": \"{\\\"template_version\\\":\\\"meeting-note-v1\\\"}\"",
                "\"content\": {\"template_version\":\"meeting-note-v1\"}",
            ),
            SUCCESS_RESPONSE.replace("]", ",${SUCCESS_CHOICE}]"),
            responseWithContent("x".repeat(128_001)),
        )
        responses.forEach { body ->
            val fixture = structuredResponding(body)
            try {
                assertFailure(MeetingNoteGenerationFailureKind.INVALID_RESPONSE, fixture.port.generate(request()))
            } finally {
                fixture.port.close()
            }
        }

        val wrongType = structuredResponding(SUCCESS_RESPONSE, contentType = ContentType.Text.Plain)
        val oversizedBody = structuredResponding("x".repeat(512 * 1_024 + 1))
        try {
            assertFailure(MeetingNoteGenerationFailureKind.INVALID_RESPONSE, wrongType.port.generate(request()))
            assertFailure(MeetingNoteGenerationFailureKind.INVALID_RESPONSE, oversizedBody.port.generate(request()))
        } finally {
            wrongType.port.close()
            oversizedBody.port.close()
        }
    }

    @Test
    internal fun `transport and unexpected failures are sanitized`() = runTest {
        listOf(
            IOException("private transport detail"),
            IllegalStateException("private runtime detail"),
        ).forEach { failure ->
            val fixture = structuredThrowing(failure)
            try {
                assertFailure(
                    MeetingNoteGenerationFailureKind.REMOTE_FAILURE,
                    fixture.port.generate(request()),
                )
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    internal fun `credential cancellation remains cooperative`() = runTest {
        val fixture = structuredResponding(
            SUCCESS_RESPONSE,
            credentials = MistralCredentialProvider { throw CancellationException("stop") },
        )
        try {
            assertFailsWith<CancellationException> { fixture.port.generate(request()) }
            assertEquals(0, fixture.requests.size)
        } finally {
            fixture.port.close()
        }
    }

    private fun assertFailure(expected: MeetingNoteGenerationFailureKind, result: MeetingNoteGenerationResult) {
        assertEquals(expected, assertIs<MeetingNoteGenerationResult.Failed>(result).kind)
    }
}

private data class StructuredFixture(val port: MistralMeetingNoteGenerationPort, val requests: List<HttpRequestData>)

private fun structuredResponding(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    credentials: MistralCredentialProvider = MistralCredentialProvider { "synthetic-token" },
    contentType: ContentType = ContentType.Application.Json,
): StructuredFixture {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
        requests += request
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, contentType.toString()),
        )
    }
    val port = MistralMeetingNoteGenerationPort.create(
        credentials,
        MistralChatConfiguration("configured-model"),
        createMistralHttpClient(engine),
    )
    return StructuredFixture(port, requests)
}

private fun structuredThrowing(failure: Throwable): StructuredFixture {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
        requests += request
        throw failure
    }
    val port = MistralMeetingNoteGenerationPort.create(
        MistralCredentialProvider { "synthetic-token" },
        MistralChatConfiguration("configured-model"),
        createMistralHttpClient(engine),
    )
    return StructuredFixture(port, requests)
}

private fun request(schemaJson: String = """{"type":"object"}"""): MeetingNoteGenerationRequest =
    MeetingNoteGenerationRequest(
        instructions = "extract safely",
        sourceJson = """{"pages":[]}""",
        schemaName = "note_v1",
        schemaJson = schemaJson,
        maximumOutputTokens = 512,
    )

private fun responseWithContent(content: String): String = """
    {
      "model": "mistral-small-latest",
      "choices": [
        {"index": 0, "message": {"content": ${Json.encodeToString(content)}}, "finish_reason": "stop"}
      ]
    }
""".trimIndent()

private val SUCCESS_RESPONSE = responseWithContent("""{"template_version":"meeting-note-v1"}""")
private const val SUCCESS_CHOICE =
    """{"index":0,"message":{"content":"{\"template_version\":\"meeting-note-v1\"}"},"finish_reason":"stop"}"""
