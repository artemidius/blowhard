package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolCallResult
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolModelFailureKind
import io.github.artemidius.blowhard.core.domain.ToolModelMessage
import io.github.artemidius.blowhard.core.domain.ToolModelRequest
import io.github.artemidius.blowhard.core.domain.ToolModelResponse
import io.github.artemidius.blowhard.core.domain.ToolName
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

internal class MistralToolModelPortTest {
    @Test
    internal fun `request maps fixed tools and ordered call results`() = runTest {
        val fixture = toolResponding(TEXT_RESPONSE)
        try {
            assertIs<ToolModelResponse.Text>(fixture.port.next(request(withHistory = true)))
            val sent = fixture.requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("Bearer synthetic-token", sent.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(sent.body.toByteArray().decodeToString()).jsonObject
            assertEquals("configured-model", body.getValue("model").jsonPrimitive.content)
            assertFalse(body.getValue("stream").jsonPrimitive.boolean)
            assertEquals(4_096, body.getValue("max_tokens").jsonPrimitive.int)
            val tool = body.getValue("tools").jsonArray.single().jsonObject
                .getValue("function").jsonObject
            assertEquals("search_text", tool.getValue("name").jsonPrimitive.content)
            assertEquals("object", tool.getValue("parameters").jsonObject.getValue("type").jsonPrimitive.content)
            val messages = body.getValue("messages").jsonArray
            assertEquals(listOf("user", "assistant", "tool", "tool"), messages.map { role(it) })
            assertEquals("call-1", messages[2].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
            assertEquals("call-2", messages[3].jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    internal fun `complete calls map with identity order arguments and mixed text`() = runTest {
        val response = """
            {"choices":[{"index":0,"finish_reason":"tool_calls","message":{
              "role":"assistant","content":"mixed text","tool_calls":[
                {"id":"call-a","type":"function","function":{"name":"search_text","arguments":"{\"query\":\"a\"}"}},
                {"id":"call-b","type":"function","function":{"name":"search_text","arguments":"{\"query\":\"b\"}"}}
              ]}}]}
        """.trimIndent()
        val fixture = toolResponding(response)
        try {
            val calls = assertIs<ToolModelResponse.Calls>(fixture.port.next(request()))
            assertEquals(listOf("call-a", "call-b"), calls.calls.map { it.id.value })
            assertEquals("{\"query\":\"a\"}", calls.calls.first().argumentsJson)
            assertEquals("mixed text", calls.accompanyingText)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    internal fun `malformed fragmented and oversized responses fail closed`() = runTest {
        val bodies = listOf(
            "{}",
            "not-json",
            TEXT_RESPONSE.replace("\"stop\"", "\"length\""),
            TEXT_RESPONSE.replace("\"index\":0", "\"index\":1"),
            TEXT_RESPONSE.replace("\"Safe answer\"", "null"),
            TOOL_RESPONSE.replace("\"arguments\":\"{}\"", "\"arguments\":null"),
            TOOL_RESPONSE.replace("\"function\"", "\"other\""),
            TOOL_RESPONSE.replace(
                "]}",
                "]},{\"index\":1,\"finish_reason\":\"stop\",\"message\":{\"content\":\"x\"}}]}",
            ),
        )
        bodies.forEach { body ->
            val fixture = toolResponding(body)
            try {
                assertFailure(ToolModelFailureKind.INVALID_RESPONSE, fixture.port.next(request()))
            } finally {
                fixture.port.close()
            }
        }
        val oversized = toolResponding("x".repeat(512 * 1_024 + 1))
        try {
            assertFailure(ToolModelFailureKind.INVALID_RESPONSE, oversized.port.next(request()))
        } finally {
            oversized.port.close()
        }
    }

    @Test
    internal fun `credentials HTTP failures and cancellation remain safe`() = runTest {
        val missing = toolResponding(TEXT_RESPONSE, credentials = MistralCredentialProvider { null })
        try {
            assertFailure(ToolModelFailureKind.AUTHENTICATION, missing.port.next(request()))
            assertEquals(0, missing.requests.size)
        } finally {
            missing.port.close()
        }
        mapOf(
            HttpStatusCode.Unauthorized to ToolModelFailureKind.AUTHENTICATION,
            HttpStatusCode.TooManyRequests to ToolModelFailureKind.RATE_LIMITED,
            HttpStatusCode.BadRequest to ToolModelFailureKind.INVALID_RESPONSE,
            HttpStatusCode.InternalServerError to ToolModelFailureKind.UNAVAILABLE,
        ).forEach { (status, expected) ->
            val fixture = toolResponding("{\"private\":true}", status)
            try {
                assertFailure(expected, fixture.port.next(request()))
            } finally {
                fixture.port.close()
            }
        }
        val cancelled = toolResponding(
            TEXT_RESPONSE,
            credentials = MistralCredentialProvider { throw CancellationException("stop") },
        )
        try {
            assertFailsWith<CancellationException> { cancelled.port.next(request()) }
        } finally {
            cancelled.port.close()
        }
    }

    private fun assertFailure(expected: ToolModelFailureKind, response: ToolModelResponse) {
        assertEquals(expected, assertIs<ToolModelResponse.Failed>(response).kind)
    }
}

private data class ToolFixture(val port: MistralToolModelPort, val requests: List<HttpRequestData>)

private fun toolResponding(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    credentials: MistralCredentialProvider = MistralCredentialProvider { "synthetic-token" },
): ToolFixture {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
        requests += request
        respond(
            body,
            status,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
    return ToolFixture(
        MistralToolModelPort.create(
            credentials,
            MistralChatConfiguration("configured-model"),
            createMistralHttpClient(engine),
        ),
        requests,
    )
}

private fun request(withHistory: Boolean = false): ToolModelRequest {
    val calls = listOf(call("1"), call("2"))
    val messages = mutableListOf<ToolModelMessage>(ToolModelMessage.User("Find release notes"))
    if (withHistory) {
        messages += ToolModelMessage.AssistantCalls(calls)
        messages += ToolModelMessage.ToolResults(
            calls.map { ToolCallResult(it.id, it.name, ToolCallOutcome.SUCCEEDED, "{\"matches\":[]}") },
        )
    }
    return ToolModelRequest(
        definitions = listOf(
            ToolDefinition(ToolName("search_text"), "Search selected text", TOOL_SCHEMA),
        ),
        messages = messages,
    )
}

private fun call(id: String): ToolCallProposal =
    ToolCallProposal(ToolCallId("call-$id"), ToolName("search_text"), "{\"query\":\"$id\"}")

private fun role(value: kotlinx.serialization.json.JsonElement): String =
    value.jsonObject.getValue("role").jsonPrimitive.content

private const val TOOL_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"}},"required":["query"]}"""
private const val TEXT_RESPONSE =
    """{"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Safe answer"}}]}"""
private const val TOOL_RESPONSE =
    """{"choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[{"id":"call-1","type":"function","function":{"name":"search_text","arguments":"{}"}}]}}]}"""
