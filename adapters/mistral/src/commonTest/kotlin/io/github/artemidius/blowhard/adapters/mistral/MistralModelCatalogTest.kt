package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.CapabilitySupport
import io.github.artemidius.blowhard.core.domain.ModelAvailability
import io.github.artemidius.blowhard.core.domain.ModelCatalogFailureKind
import io.github.artemidius.blowhard.core.domain.ModelCatalogResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MistralModelCatalogTest {
    @Test
    fun `documented model response maps conservatively and ignores unknown fields`() = runTest {
        val fixture = catalogResponding(
            body = SUCCESS_RESPONSE,
            status = HttpStatusCode.OK,
        )
        try {
            val models = assertIs<ModelCatalogResult.Success>(fixture.catalog.listModels()).models

            assertEquals(listOf("archived-model", "chat-model"), models.map { it.id })
            assertEquals(ModelAvailability.ARCHIVED, models[0].availability)
            assertEquals(CapabilitySupport.UNKNOWN, models[0].capabilities.chatCompletion)
            assertEquals(ModelAvailability.AVAILABLE, models[1].availability)
            assertEquals(listOf("chat-latest"), models[1].aliases)
            assertEquals(32_768, models[1].contextWindowTokens)
            assertEquals(CapabilitySupport.SUPPORTED, models[1].capabilities.chatCompletion)
            assertEquals(CapabilitySupport.UNSUPPORTED, models[1].capabilities.fillInMiddle)
            assertEquals(CapabilitySupport.SUPPORTED, models[1].capabilities.functionCalling)
        } finally {
            fixture.catalog.close()
        }
    }

    @Test
    fun `request uses the approved endpoint method and bearer header`() = runTest {
        val fixture = catalogResponding(EMPTY_RESPONSE)
        try {
            assertIs<ModelCatalogResult.Success>(fixture.catalog.listModels())

            val request = fixture.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("https://api.mistral.ai/v1/models", request.url.toString())
            assertEquals("Bearer synthetic-token", request.headers[HttpHeaders.Authorization])
            assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
        } finally {
            fixture.catalog.close()
        }
    }

    @Test
    fun `missing failed and unsafe credentials never reach the network`() = runTest {
        val credentials = listOf<MistralCredentialProvider>(
            MistralCredentialProvider { null },
            MistralCredentialProvider { error("private credential-store detail") },
            MistralCredentialProvider { "" },
            MistralCredentialProvider { "contains whitespace" },
            MistralCredentialProvider { "line\nbreak" },
            MistralCredentialProvider { "x".repeat(4_097) },
        )

        credentials.forEach { provider ->
            var requests = 0
            val client = createMistralHttpClient(
                MockEngine {
                    requests += 1
                    respond(EMPTY_RESPONSE)
                },
            )
            val catalog = MistralModelCatalog.create(provider, client)
            try {
                assertFailure(ModelCatalogFailureKind.CREDENTIAL_UNAVAILABLE, catalog.listModels())
                assertEquals(0, requests)
            } finally {
                catalog.close()
            }
        }
    }

    @Test
    fun `documented HTTP statuses map without retaining response bodies`() = runTest {
        val expected = mapOf(
            HttpStatusCode.BadRequest to ModelCatalogFailureKind.INVALID_REQUEST,
            HttpStatusCode.Unauthorized to ModelCatalogFailureKind.UNAUTHORIZED,
            HttpStatusCode.PaymentRequired to ModelCatalogFailureKind.PAYMENT_REQUIRED,
            HttpStatusCode.Forbidden to ModelCatalogFailureKind.FORBIDDEN,
            HttpStatusCode.NotFound to ModelCatalogFailureKind.NOT_FOUND,
            HttpStatusCode.UnprocessableEntity to ModelCatalogFailureKind.INVALID_REQUEST,
            HttpStatusCode.TooManyRequests to ModelCatalogFailureKind.RATE_LIMITED,
            HttpStatusCode.RequestTimeout to ModelCatalogFailureKind.UNAVAILABLE,
            HttpStatusCode.InternalServerError to ModelCatalogFailureKind.UNAVAILABLE,
            HttpStatusCode.BadGateway to ModelCatalogFailureKind.UNAVAILABLE,
            HttpStatusCode.ServiceUnavailable to ModelCatalogFailureKind.UNAVAILABLE,
            HttpStatusCode.GatewayTimeout to ModelCatalogFailureKind.UNAVAILABLE,
        )

        expected.forEach { (status, kind) ->
            val fixture = catalogResponding("""{"message":"private provider detail"}""", status)
            try {
                assertFailure(kind, fixture.catalog.listModels())
            } finally {
                fixture.catalog.close()
            }
        }
    }

    @Test
    fun `redirect is rejected before authorization can reach another host`() = runTest {
        val fixture = catalogResponding(
            body = "",
            status = HttpStatusCode.Found,
            headers = headersOf(HttpHeaders.Location, "https://untrusted.example/models"),
        )
        try {
            assertFailure(ModelCatalogFailureKind.REDIRECT_REJECTED, fixture.catalog.listModels())
            assertEquals(1, fixture.requests.size)
            assertEquals("api.mistral.ai", fixture.requests.single().url.host)
        } finally {
            fixture.catalog.close()
        }
    }

    @Test
    fun `rate limit accepts only bounded numeric retry delay`() = runTest {
        val valid = catalogResponding(
            body = "{}",
            status = HttpStatusCode.TooManyRequests,
            headers = headersOf(HttpHeaders.RetryAfter, "17"),
        )
        val invalid = listOf(
            catalogResponding(
                body = "{}",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "999999"),
            ),
            catalogResponding(
                body = "{}",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "tomorrow"),
            ),
            catalogResponding(
                body = "{}",
                status = HttpStatusCode.TooManyRequests,
            ),
        )
        try {
            val validFailure = assertIs<ModelCatalogResult.Failure>(valid.catalog.listModels()).failure
            assertEquals(17, validFailure.retryAfterSeconds)
            invalid.forEach { fixture ->
                val invalidFailure = assertIs<ModelCatalogResult.Failure>(fixture.catalog.listModels()).failure
                assertNull(invalidFailure.retryAfterSeconds)
            }
        } finally {
            valid.catalog.close()
            invalid.forEach { it.catalog.close() }
        }
    }

    @Test
    fun `malformed and internally inconsistent success bodies are rejected`() = runTest {
        val bodies = listOf(
            "not-json",
            "{}",
            """{"data":[{"id":""}]}""",
            """{"data":[{"id":"model","max_context_length":-1}]}""",
            """{"data":[{"id":"duplicate"},{"id":"duplicate"}]}""",
        )

        bodies.forEach { body ->
            val fixture = catalogResponding(body)
            try {
                assertFailure(ModelCatalogFailureKind.INVALID_RESPONSE, fixture.catalog.listModels())
            } finally {
                fixture.catalog.close()
            }
        }
    }

    @Test
    fun `network failures are redacted and cancellation remains cooperative`() = runTest {
        val failingClient = createMistralHttpClient(MockEngine { throw IOException("private network detail") })
        val failingCatalog = MistralModelCatalog.create(validCredentials, failingClient)
        val cancelledCatalog = MistralModelCatalog.create(
            MistralCredentialProvider { throw CancellationException("cancel") },
            createMistralHttpClient(MockEngine { respond(EMPTY_RESPONSE) }),
        )
        try {
            assertFailure(ModelCatalogFailureKind.UNAVAILABLE, failingCatalog.listModels())
            assertFailsWith<CancellationException> { cancelledCatalog.listModels() }
        } finally {
            failingCatalog.close()
            cancelledCatalog.close()
        }
    }

    private fun assertFailure(expected: ModelCatalogFailureKind, result: ModelCatalogResult) {
        assertEquals(expected, assertIs<ModelCatalogResult.Failure>(result).failure.kind)
    }
}

private data class CatalogFixture(val catalog: MistralModelCatalog, val requests: List<HttpRequestData>)

private fun catalogResponding(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    headers: io.ktor.http.Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
): CatalogFixture {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
        requests += request
        respond(content = body, status = status, headers = headers)
    }
    return CatalogFixture(
        catalog = MistralModelCatalog.create(validCredentials, createMistralHttpClient(engine)),
        requests = requests,
    )
}

private val validCredentials = MistralCredentialProvider { "synthetic-token" }

private const val EMPTY_RESPONSE = """{"object":"list","data":[]}"""

private const val SUCCESS_RESPONSE = """
    {
      "object": "list",
      "unknown_root": true,
      "data": [
        {
          "id": "chat-model",
          "aliases": ["chat-latest", "", "chat-latest"],
          "max_context_length": 32768,
          "archived": false,
          "unknown_card_field": "ignored",
          "capabilities": {
            "completion_chat": true,
            "completion_fim": false,
            "function_calling": true,
            "fine_tuning": false,
            "vision": true,
            "classification": false,
            "future_capability": true
          }
        },
        {
          "id": "archived-model",
          "archived": true
        }
      ]
    }
"""
