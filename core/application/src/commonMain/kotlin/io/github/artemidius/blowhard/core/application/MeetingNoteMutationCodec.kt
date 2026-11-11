package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object MeetingNoteMutationCodec {
    fun encode(note: MeetingNoteRecord): String = JsonObject(
        mapOf(
            "id" to JsonPrimitive(note.id.value),
            "source_document_id" to JsonPrimitive(note.sourceDocumentId.value),
            "template_version" to JsonPrimitive(note.templateVersion),
            "model_id" to JsonPrimitive(note.modelId),
            "generated_content" to note.generatedContent.toJson(),
            "edited_content" to (note.editedContent?.toJson() ?: JsonNull),
            "created_at_epoch_ms" to JsonPrimitive(note.createdAtEpochMilliseconds),
            "updated_at_epoch_ms" to JsonPrimitive(note.updatedAtEpochMilliseconds),
            "revision" to JsonPrimitive(note.revision),
        ),
    ).toString()
}

private fun MeetingNoteContent.toJson(): JsonObject = JsonObject(
    mapOf(
        "title" to (title?.let(::JsonPrimitive) ?: JsonNull),
        "decisions" to JsonArray(decisions.map(MeetingDecision::toJson)),
        "action_items" to JsonArray(actionItems.map(MeetingActionItem::toJson)),
    ),
)

private fun MeetingDecision.toJson(): JsonObject = JsonObject(
    mapOf(
        "summary" to JsonPrimitive(summary),
        "sources" to JsonArray(sources.map(MeetingNoteSourceReference::toJson)),
    ),
)

private fun MeetingActionItem.toJson(): JsonObject = JsonObject(
    mapOf(
        "description" to JsonPrimitive(description),
        "owner" to (owner?.let(::JsonPrimitive) ?: JsonNull),
        "due_date" to (dueDate?.value?.let(::JsonPrimitive) ?: JsonNull),
        "sources" to JsonArray(sources.map(MeetingNoteSourceReference::toJson)),
    ),
)

private fun MeetingNoteSourceReference.toJson(): JsonObject = JsonObject(
    mapOf("page_index" to JsonPrimitive(pageIndex), "excerpt" to JsonPrimitive(excerpt)),
)
