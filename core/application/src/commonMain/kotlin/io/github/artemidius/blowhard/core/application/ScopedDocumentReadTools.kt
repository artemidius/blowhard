package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolName

/** Immutable document identities explicitly attached to one conversation. */
public data class DocumentReadScope(
    public val conversationId: ConversationId,
    public val documentIds: Set<DocumentId>,
) {
    init {
        require(documentIds.isNotEmpty() && documentIds.size <= MAXIMUM_SCOPED_DOCUMENTS) {
            "Document read scope is invalid"
        }
    }
}

/** Builds deterministic read-only tools bound to one immutable attachment scope. */
public object ScopedDocumentReadTools {
    /** Returns search and page-read registrations; neither can expand [scope]. */
    public fun create(scope: DocumentReadScope, store: DocumentProcessingStore): List<RegisteredTool> = listOf(
        RegisteredTool(
            ToolDefinition(ToolName(SEARCH_TOOL_NAME), "Search text in one attached document", SEARCH_SCHEMA),
            DocumentSearchExecutor(scope, store)::execute,
        ),
        RegisteredTool(
            ToolDefinition(ToolName(READ_TOOL_NAME), "Read one page from an attached document", READ_SCHEMA),
            DocumentPageExecutor(scope, store)::execute,
        ),
    )
}

private class DocumentSearchExecutor(
    private val scope: DocumentReadScope,
    private val store: DocumentProcessingStore,
) {
    suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val arguments = SearchArguments.parse(argumentsJson)
        return if (arguments == null) {
            invalidArguments()
        } else {
            scope.read(arguments.documentId, store) { extraction ->
                extraction.search(arguments.query, arguments.limit).completed()
            }
        }
    }
}

private class DocumentPageExecutor(private val scope: DocumentReadScope, private val store: DocumentProcessingStore) {
    suspend fun execute(argumentsJson: String): ToolExecutionResult {
        val arguments = PageArguments.parse(argumentsJson)
        return if (arguments == null) {
            invalidArguments()
        } else {
            scope.read(arguments.documentId, store) { extraction -> extraction.readPage(arguments.pageIndex) }
        }
    }
}

private suspend fun DocumentReadScope.read(
    documentId: DocumentId,
    store: DocumentProcessingStore,
    found: (DocumentExtraction) -> ToolExecutionResult,
): ToolExecutionResult {
    if (documentId !in documentIds) return rejected("document_not_attached")
    return when (val result = store.findExtraction(documentId)) {
        is DocumentExtractionStoreLookupResult.Found -> found(result.extraction)
        DocumentExtractionStoreLookupResult.NotProcessed -> rejected("document_not_processed")
        DocumentExtractionStoreLookupResult.SourceNotFound -> rejected("document_not_found")
        DocumentExtractionStoreLookupResult.Unavailable -> failed("storage_unavailable")
    }
}

private const val MAXIMUM_SCOPED_DOCUMENTS = 8
private const val SEARCH_TOOL_NAME = "search_text"
private const val READ_TOOL_NAME = "read_page"
private const val SEARCH_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"document_id":{"type":"string"},"query":{"type":"string"},"limit":{"type":"integer"}},"required":["document_id","query","limit"]}"""
private const val READ_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"document_id":{"type":"string"},"page_index":{"type":"integer"}},"required":["document_id","page_index"]}"""
