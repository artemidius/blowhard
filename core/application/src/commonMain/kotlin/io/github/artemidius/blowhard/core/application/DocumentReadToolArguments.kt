package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal data class SearchArguments(val documentId: DocumentId, val query: String, val limit: Int) {
    companion object {
        fun parse(raw: String): SearchArguments? = runCatching {
            val value = requireNotNull(raw.argumentsOrNull())
            val query = requireNotNull(value.string("query"))
            val limit = requireNotNull(value.integer("limit"))
            require(query.isNotBlank() && query.length <= MAXIMUM_QUERY_CHARACTERS)
            require(limit in 1..MAXIMUM_SEARCH_RESULTS)
            SearchArguments(requireNotNull(value.documentIdOrNull()), query, limit)
        }.getOrNull()
    }
}

internal data class PageArguments(val documentId: DocumentId, val pageIndex: Int) {
    companion object {
        fun parse(raw: String): PageArguments? = runCatching {
            val value = requireNotNull(raw.argumentsOrNull())
            val pageIndex = requireNotNull(value.integer("page_index"))
            require(pageIndex >= 0)
            PageArguments(requireNotNull(value.documentIdOrNull()), pageIndex)
        }.getOrNull()
    }
}

private fun String.argumentsOrNull(): JsonObject? = runCatching {
    READ_TOOL_JSON.parseToJsonElement(this) as? JsonObject
}.getOrNull()

private fun JsonObject.documentIdOrNull(): DocumentId? = string("document_id")?.let { value ->
    runCatching { DocumentId(value) }.getOrNull()
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)
    ?.takeIf(JsonPrimitive::isString)
    ?.content

private fun JsonObject.integer(name: String): Int? = (this[name] as? JsonPrimitive)
    ?.takeUnless(JsonPrimitive::isString)
    ?.intOrNull

private const val MAXIMUM_QUERY_CHARACTERS = 256
private const val MAXIMUM_SEARCH_RESULTS = 8
private val READ_TOOL_JSON = Json { isLenient = false }
