package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteExport
import io.github.artemidius.blowhard.core.api.MeetingNoteExportFormat
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object MeetingNoteExporter {
    fun export(note: MeetingNoteRecord, format: MeetingNoteExportFormat): MeetingNoteExport? {
        val content = when (format) {
            MeetingNoteExportFormat.JSON -> note.content.toJson()
            MeetingNoteExportFormat.MARKDOWN -> note.content.toMarkdown()
        }
        if (content.isBlank() || content.length > MeetingNoteExport.MAX_CONTENT_CHARACTERS) return null
        return when (format) {
            MeetingNoteExportFormat.JSON -> MeetingNoteExport("meeting-note.json", "application/json", content)
            MeetingNoteExportFormat.MARKDOWN -> MeetingNoteExport("meeting-note.md", "text/markdown", content)
        }
    }

    private fun MeetingNoteContent.toJson(): String = JsonObject(
        mapOf(
            "template_version" to JsonPrimitive(MeetingNoteTemplateV1.VERSION),
            "title" to title.jsonString(),
            "decisions" to JsonArray(decisions.map { it.toJson() }),
            "action_items" to JsonArray(actionItems.map { it.toJson() }),
        ),
    ).toString()

    private fun MeetingDecision.toJson(): JsonObject = JsonObject(
        mapOf(
            "summary" to JsonPrimitive(summary),
            "sources" to JsonArray(sources.map { it.toJson() }),
        ),
    )

    private fun MeetingActionItem.toJson(): JsonObject = JsonObject(
        mapOf(
            "description" to JsonPrimitive(description),
            "owner" to owner.jsonString(),
            "due_date" to dueDate?.value.jsonString(),
            "sources" to JsonArray(sources.map { it.toJson() }),
        ),
    )

    private fun MeetingNoteSourceReference.toJson(): JsonObject = JsonObject(
        mapOf("page_index" to JsonPrimitive(pageIndex), "excerpt" to JsonPrimitive(excerpt)),
    )

    private fun MeetingNoteContent.toMarkdown(): String = buildString {
        append("# ").appendLine(title ?: "Meeting note")
        appendLine().appendLine("## Decisions")
        if (decisions.isEmpty()) appendLine("_None extracted._")
        decisions.forEach { decision ->
            append("- ").appendLine(decision.summary)
            appendSources(decision.sources)
        }
        appendLine().appendLine("## Action items")
        if (actionItems.isEmpty()) appendLine("_None extracted._")
        actionItems.forEach { item ->
            append("- ").append(item.description)
            item.owner?.let { append(" — ").append(it) }
            item.dueDate?.let { append(" — due ").append(it.value) }
            appendLine()
            appendSources(item.sources)
        }
    }.trimEnd()

    private fun StringBuilder.appendSources(sources: List<MeetingNoteSourceReference>) {
        sources.forEach { source ->
            append("  - Source page ").append(source.pageIndex + 1).append(": ")
            appendLine(source.excerpt.replace('\n', ' '))
        }
    }

    private fun String?.jsonString() = this?.let(::JsonPrimitive) ?: JsonNull
}
