package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.domain.DocumentOcrFailureKind
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

internal class MistralDocumentOcrPortTest {
    @Test
    internal fun `pdf request uses documented data URI contract and maps ordered pages`() = runTest {
        val fixture = ocrResponding(SUCCESS_RESPONSE)
        try {
            val result = assertIs<DocumentOcrPortResult.Processed>(fixture.port.process(pdfRequest()))

            assertEquals("mistral-ocr-latest", result.processor)
            assertEquals(listOf(0, 1), result.pages.map { it.index })
            assertEquals(listOf("# One", "Two"), result.pages.map { it.markdown })
            val sent = fixture.requests.single()
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("https://api.mistral.ai/v1/ocr", sent.url.toString())
            assertEquals("Bearer synthetic-token", sent.headers[HttpHeaders.Authorization])
            assertEquals(ContentType.Application.Json.toString(), sent.headers[HttpHeaders.Accept])
            val body = Json.parseToJsonElement(sent.body.toByteArray().decodeToString()).jsonObject
            assertEquals("mistral-ocr-latest", body.getValue("model").jsonPrimitive.content)
            assertEquals("markdown", body.getValue("table_format").jsonPrimitive.content)
            assertFalse(body.getValue("include_image_base64").jsonPrimitive.boolean)
            val document = body.getValue("document").jsonObject
            assertEquals("document_url", document.getValue("type").jsonPrimitive.content)
            assertEquals(
                "data:application/pdf;base64,AQID",
                document.getValue("document_url").jsonPrimitive.content,
            )
            assertFalse("image_url" in document)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    internal fun `image request selects a format-specific image data URI`() = runTest {
        val fixture = ocrResponding("""{"pages":[{"index":0,"markdown":"scan"}]}""")
        try {
            assertIs<DocumentOcrPortResult.Processed>(fixture.port.process(imageRequest(DocumentFormat.JPEG)))

            val body = Json.parseToJsonElement(
                fixture.requests.single().body.toByteArray().decodeToString(),
            ).jsonObject
            val document = body.getValue("document").jsonObject
            assertEquals("image_url", document.getValue("type").jsonPrimitive.content)
            assertEquals("data:image/jpeg;base64,AQID", document.getValue("image_url").jsonPrimitive.content)
            assertFalse("document_url" in document)
        } finally {
            fixture.port.close()
        }
    }

    @Test
    internal fun `missing or unsafe credentials never reach the network`() = runTest {
        val credentials = listOf(
            MistralCredentialProvider { null },
            MistralCredentialProvider { "contains whitespace" },
            MistralCredentialProvider { error("private credential detail") },
        )
        credentials.forEach { provider ->
            val fixture = ocrResponding(SUCCESS_RESPONSE, credentials = provider)
            try {
                assertFailure(DocumentOcrFailureKind.AUTHENTICATION, fixture.port.process(pdfRequest()))
                assertEquals(0, fixture.requests.size)
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    internal fun `HTTP failures are classified without retaining provider bodies`() = runTest {
        val cases = mapOf(
            HttpStatusCode.BadRequest to DocumentOcrFailureKind.INPUT_REJECTED,
            HttpStatusCode.PayloadTooLarge to DocumentOcrFailureKind.INPUT_REJECTED,
            HttpStatusCode.Unauthorized to DocumentOcrFailureKind.AUTHENTICATION,
            HttpStatusCode.Forbidden to DocumentOcrFailureKind.AUTHENTICATION,
            HttpStatusCode.TooManyRequests to DocumentOcrFailureKind.RATE_LIMITED,
            HttpStatusCode.InternalServerError to DocumentOcrFailureKind.REMOTE_FAILURE,
        )
        cases.forEach { (status, failure) ->
            val fixture = ocrResponding("""{"detail":"private provider body"}""", status)
            try {
                assertFailure(failure, fixture.port.process(pdfRequest()))
            } finally {
                fixture.port.close()
            }
        }
    }

    @Test
    internal fun `malformed and bounded oversized responses are rejected`() = runTest {
        val malformed = listOf("not-json", "{}", """{"pages":null}""")
        malformed.forEach { body ->
            val fixture = ocrResponding(body)
            try {
                assertFailure(DocumentOcrFailureKind.INVALID_RESPONSE, fixture.port.process(pdfRequest()))
            } finally {
                fixture.port.close()
            }
        }

        val wrongContentType = ocrResponding(SUCCESS_RESPONSE, contentType = ContentType.Text.Plain)
        try {
            assertFailure(DocumentOcrFailureKind.INVALID_RESPONSE, wrongContentType.port.process(pdfRequest()))
        } finally {
            wrongContentType.port.close()
        }

        val oversized = ocrResponding("x".repeat(4 * 1_024 * 1_024 + 1))
        try {
            assertFailure(DocumentOcrFailureKind.INVALID_RESPONSE, oversized.port.process(pdfRequest()))
        } finally {
            oversized.port.close()
        }
    }

    @Test
    internal fun `credential cancellation remains cooperative`() = runTest {
        val fixture = ocrResponding(
            SUCCESS_RESPONSE,
            credentials = MistralCredentialProvider { throw CancellationException("stop") },
        )
        try {
            assertFailsWith<CancellationException> { fixture.port.process(pdfRequest()) }
            assertNull(fixture.requests.singleOrNull())
        } finally {
            fixture.port.close()
        }
    }

    private fun assertFailure(expected: DocumentOcrFailureKind, result: DocumentOcrPortResult) {
        assertEquals(expected, assertIs<DocumentOcrPortResult.Failed>(result).kind)
    }
}

private data class OcrFixture(val port: MistralDocumentOcrPort, val requests: List<HttpRequestData>)

private fun ocrResponding(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    credentials: MistralCredentialProvider = MistralCredentialProvider { "synthetic-token" },
    contentType: ContentType = ContentType.Application.Json,
): OcrFixture {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
        requests += request
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, contentType.toString()),
        )
    }
    return OcrFixture(MistralDocumentOcrPort.create(credentials, createMistralHttpClient(engine)), requests)
}

private fun pdfRequest(): DocumentOcrRequest = DocumentOcrRequest(
    document = ImportedDocument(
        id = DocumentId("pdf"),
        displayName = "document.pdf",
        sizeBytes = 3,
        importedAtEpochMilliseconds = 1,
        content = DocumentContentDescriptor.Pdf(2),
    ),
    content = byteArrayOf(1, 2, 3),
)

private fun imageRequest(format: DocumentFormat): DocumentOcrRequest = DocumentOcrRequest(
    document = ImportedDocument(
        id = DocumentId("image"),
        displayName = "scan.jpg",
        sizeBytes = 3,
        importedAtEpochMilliseconds = 1,
        content = DocumentContentDescriptor.Image(format, 10, 20),
    ),
    content = byteArrayOf(1, 2, 3),
)

private const val SUCCESS_RESPONSE = """
    {
      "pages": [
        {"index": 0, "markdown": "# One", "images": []},
        {"index": 1, "markdown": "Two", "future_field": true}
      ],
      "model": "mistral-ocr-2505-completed",
      "unknown_root": true
    }
"""
