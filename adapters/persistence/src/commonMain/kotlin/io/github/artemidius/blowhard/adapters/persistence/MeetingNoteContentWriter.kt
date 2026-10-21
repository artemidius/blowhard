package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object MeetingNoteContentWriter {
    fun encode(content: MeetingNoteContent): String = JsonObject(
        mapOf(
            "title" to content.title.jsonString(),
            "decisions" to JsonArray(content.decisions.map { it.encode() }),
            "action_items" to JsonArray(content.actionItems.map { it.encode() }),
        ),
    ).toString()
}

private fun MeetingDecision.encode(): JsonObject = JsonObject(
    mapOf("summary" to JsonPrimitive(summary), "sources" to JsonArray(sources.map { it.encode() })),
)

private fun MeetingActionItem.encode(): JsonObject = JsonObject(
    mapOf(
        "description" to JsonPrimitive(description),
        "owner" to owner.jsonString(),
        "due_date" to dueDate?.value.jsonString(),
        "sources" to JsonArray(sources.map { it.encode() }),
    ),
)

private fun MeetingNoteSourceReference.encode(): JsonObject = JsonObject(
    mapOf("page_index" to JsonPrimitive(pageIndex), "excerpt" to JsonPrimitive(excerpt)),
)

private fun String?.jsonString(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
