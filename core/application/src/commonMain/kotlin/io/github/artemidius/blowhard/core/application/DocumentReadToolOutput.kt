package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun DocumentExtraction.readPage(pageIndex: Int): ToolExecutionResult {
    val page = pages.getOrNull(pageIndex) ?: return rejected("page_not_found")
    return if (page.markdown.length > MAXIMUM_PAGE_CHARACTERS) {
        rejected("content_too_large")
    } else {
        JsonObject(
            evidence(this, pageIndex) + mapOf(
                "content_trust" to JsonPrimitive("untrusted_document_content"),
                "policy_effect" to JsonPrimitive("none"),
                "content" to JsonPrimitive(page.markdown),
            ),
        ).completed()
    }
}

internal fun DocumentExtraction.search(query: String, limit: Int): JsonObject {
    val normalized = query.lowercase()
    val matches = pages.mapNotNull { page ->
        val offset = page.markdown.lowercase().indexOf(normalized)
        offset.takeIf { it >= 0 }?.let {
            val start = (it - SEARCH_CONTEXT_CHARACTERS).coerceAtLeast(0)
            val end = (it + query.length + SEARCH_CONTEXT_CHARACTERS).coerceAtMost(page.markdown.length)
            JsonObject(
                evidence(this, page.index) + mapOf(
                    "excerpt" to JsonPrimitive(page.markdown.substring(start, end)),
                ),
            )
        }
    }.take(limit)
    return JsonObject(
        mapOf(
            "content_trust" to JsonPrimitive("untrusted_document_content"),
            "policy_effect" to JsonPrimitive("none"),
            "matches" to JsonArray(matches),
        ),
    )
}

private fun evidence(extraction: DocumentExtraction, pageIndex: Int): Map<String, JsonPrimitive> = mapOf(
    "document_id" to JsonPrimitive(extraction.sourceDocumentId.value),
    "page_index" to JsonPrimitive(pageIndex),
    "processor" to JsonPrimitive(extraction.processor),
    "processed_at_epoch_ms" to JsonPrimitive(extraction.processedAtEpochMilliseconds),
)

internal fun JsonObject.completed(): ToolExecutionResult = ToolExecutionResult.Completed(toString())
internal fun invalidArguments(): ToolExecutionResult = rejected("invalid_arguments")
internal fun rejected(error: String): ToolExecutionResult = ToolExecutionResult.Rejected(errorJson(error))
internal fun failed(error: String): ToolExecutionResult = ToolExecutionResult.Failed(errorJson(error))
private fun errorJson(error: String): String = JsonObject(mapOf("error" to JsonPrimitive(error))).toString()

private const val MAXIMUM_PAGE_CHARACTERS = 16 * 1_024
private const val SEARCH_CONTEXT_CHARACTERS = 192
