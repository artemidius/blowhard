package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScopedDocumentReadToolsTest {
    @Test
    fun `scope requires a bounded non-empty attachment set`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentReadScope(ConversationId("conversation"), emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentReadScope(
                ConversationId("conversation"),
                (0..8).map { DocumentId("document-$it") }.toSet(),
            )
        }
    }

    @Test
    fun `search is deterministic capped and labels retrieved text as untrusted`() = runTest {
        val pages = (0..9).map { index -> ExtractedDocumentPage(index, "Page $index says Release is Friday.") }
        val result = registry(FakeProcessingStore(found(extraction(ATTACHED, pages)))).execute(
            proposal("search_text", "{\"document_id\":\"attached\",\"query\":\"release\",\"limit\":3}"),
        )

        assertEquals(ToolCallOutcome.SUCCEEDED, result.outcome)
        val output = Json.parseToJsonElement(result.outputJson).jsonObject
        assertEquals("untrusted_document_content", output.getValue("content_trust").jsonPrimitive.content)
        assertEquals("none", output.getValue("policy_effect").jsonPrimitive.content)
        assertEquals(3, output.getValue("matches").jsonArray.size)
        assertEquals(
            "attached",
            output.getValue("matches").jsonArray.first().jsonObject
                .getValue("document_id").jsonPrimitive.content,
        )
    }

    @Test
    fun `scope prevents cross-conversation reads before storage access`() = runTest {
        val store = FakeProcessingStore(found(extraction(OUTSIDE)))
        val result = registry(store).execute(
            proposal("read_page", "{\"document_id\":\"outside\",\"page_index\":0}"),
        )

        assertEquals(ToolCallOutcome.REJECTED, result.outcome)
        assertTrue(result.outputJson.contains("document_not_attached"))
        assertEquals(0, store.lookups)
    }

    @Test
    fun `page reads preserve exact source identity and enforce output ceiling`() = runTest {
        val normal = registry(FakeProcessingStore(found(extraction(ATTACHED)))).execute(
            proposal("read_page", "{\"document_id\":\"attached\",\"page_index\":0}"),
        )
        val output = Json.parseToJsonElement(normal.outputJson).jsonObject
        assertEquals("attached", output.getValue("document_id").jsonPrimitive.content)
        assertEquals("fixture-ocr-v1", output.getValue("processor").jsonPrimitive.content)
        assertEquals("Release is Friday.", output.getValue("content").jsonPrimitive.content)

        val oversized = extraction(ATTACHED, listOf(ExtractedDocumentPage(0, "x".repeat(16 * 1_024 + 1))))
        val denied = registry(FakeProcessingStore(found(oversized))).execute(
            proposal("read_page", "{\"document_id\":\"attached\",\"page_index\":0}"),
        )
        assertEquals(ToolCallOutcome.REJECTED, denied.outcome)
        assertTrue(denied.outputJson.contains("content_too_large"))
    }

    @Test
    fun `stale missing and invalid reads return typed bounded failures`() = runTest {
        val cases = listOf(
            DocumentExtractionStoreLookupResult.SourceNotFound to "document_not_found",
            DocumentExtractionStoreLookupResult.NotProcessed to "document_not_processed",
            DocumentExtractionStoreLookupResult.Unavailable to "storage_unavailable",
            found(extraction(ATTACHED)) to "page_not_found",
        )
        cases.forEach { (lookup, error) ->
            val result = registry(FakeProcessingStore(lookup)).execute(
                proposal("read_page", "{\"document_id\":\"attached\",\"page_index\":9}"),
            )
            assertTrue(result.outputJson.contains(error))
        }
        val invalid = registry(FakeProcessingStore(found(extraction(ATTACHED)))).execute(
            proposal("read_page", "{\"document_id\":\"attached\",\"page_index\":-1}"),
        )
        assertTrue(invalid.outputJson.contains("invalid_arguments"))
    }

    @Test
    fun `search rejects malformed and out-of-policy arguments`() = runTest {
        val registry = registry(FakeProcessingStore(found(extraction(ATTACHED))))
        val invalidArguments = listOf(
            "not-json",
            "{}",
            "{\"document_id\":\"attached\",\"query\":\"\",\"limit\":1}",
            "{\"document_id\":\"attached\",\"query\":\"${"x".repeat(257)}\",\"limit\":1}",
            "{\"document_id\":\"attached\",\"query\":\"release\",\"limit\":0}",
            "{\"document_id\":\"attached\",\"query\":\"release\",\"limit\":9}",
            "{\"document_id\":\"attached\",\"query\":\"release\",\"limit\":\"1\"}",
        )

        invalidArguments.forEach { arguments ->
            val result = registry.execute(proposal("search_text", arguments))
            assertEquals(ToolCallOutcome.REJECTED, result.outcome)
            assertTrue(result.outputJson.contains("invalid_arguments"))
        }
    }

    @Test
    fun `search returns an empty evidence list when text is absent`() = runTest {
        val result = registry(FakeProcessingStore(found(extraction(ATTACHED)))).execute(
            proposal("search_text", "{\"document_id\":\"attached\",\"query\":\"budget\",\"limit\":8}"),
        )

        assertEquals(ToolCallOutcome.SUCCEEDED, result.outcome)
        val output = Json.parseToJsonElement(result.outputJson).jsonObject
        assertTrue(output.getValue("matches").jsonArray.isEmpty())
    }

    @Test
    fun `storage cancellation remains cooperative`() = runTest {
        val store = object : FakeProcessingStore(DocumentExtractionStoreLookupResult.Unavailable) {
            override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult =
                throw CancellationException("stop")
        }
        assertFailsWith<CancellationException> {
            registry(store).execute(
                proposal("read_page", "{\"document_id\":\"attached\",\"page_index\":0}"),
            )
        }
    }

    private fun registry(store: DocumentProcessingStore): ToolRegistry = requireNotNull(
        ToolRegistry.create(
            ScopedDocumentReadTools.create(DocumentReadScope(ConversationId("conversation"), setOf(ATTACHED)), store),
        ),
    )

    private fun proposal(name: String, arguments: String): ToolCallProposal =
        ToolCallProposal(ToolCallId("call-1"), ToolName(name), arguments)
}

private open class FakeProcessingStore(private val lookup: DocumentExtractionStoreLookupResult) :
    DocumentProcessingStore {
    var lookups: Int = 0

    override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult {
        lookups += 1
        return lookup
    }

    override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult = DocumentSourceLookupResult.Unavailable
    override suspend fun saveExtraction(extraction: DocumentExtraction): DocumentExtractionSaveResult =
        DocumentExtractionSaveResult.UNAVAILABLE

    override suspend fun close(): Unit = Unit
}

private fun found(extraction: DocumentExtraction): DocumentExtractionStoreLookupResult =
    DocumentExtractionStoreLookupResult.Found(extraction)

private fun extraction(
    documentId: DocumentId,
    pages: List<ExtractedDocumentPage> = listOf(ExtractedDocumentPage(0, "Release is Friday.")),
): DocumentExtraction = DocumentExtraction(documentId, "fixture-ocr-v1", 100, pages)

private val ATTACHED = DocumentId("attached")
private val OUTSIDE = DocumentId("outside")
