package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteDate
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object MeetingNoteContentReader {
    fun decode(raw: String): MeetingNoteContent? = try {
        val root = Json.parseToJsonElement(raw).objectWithKeys(ROOT_KEYS)
        MeetingNoteContent(
            title = root.nullableString("title"),
            decisions = root.array("decisions").map { it.decodeDecision() },
            actionItems = root.array("action_items").map { it.decodeAction() },
        )
    } catch (_: Exception) {
        null
    }
}

private fun JsonElement.decodeDecision(): MeetingDecision = objectWithKeys(DECISION_KEYS).let { value ->
    MeetingDecision(value.string("summary"), value.array("sources").map { it.decodeReference() })
}

private fun JsonElement.decodeAction(): MeetingActionItem = objectWithKeys(ACTION_KEYS).let { value ->
    MeetingActionItem(
        description = value.string("description"),
        owner = value.nullableString("owner"),
        dueDate = value.nullableString("due_date")?.let(::MeetingNoteDate),
        sources = value.array("sources").map { it.decodeReference() },
    )
}

private fun JsonElement.decodeReference(): MeetingNoteSourceReference = objectWithKeys(SOURCE_KEYS).let { value ->
    MeetingNoteSourceReference(value.integer("page_index"), value.string("excerpt"))
}

private fun JsonElement.objectWithKeys(keys: Set<String>): JsonObject = (this as? JsonObject)
    ?.takeIf { it.keys == keys }
    ?: error("Unexpected meeting-note shape")

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)
    ?.takeIf(JsonPrimitive::isString)
    ?.contentOrNull
    ?: error("Unexpected meeting-note string")

private fun JsonObject.nullableString(key: String): String? = when (val value = this[key]) {
    JsonNull -> null

    is JsonPrimitive -> value.takeIf(JsonPrimitive::isString)?.contentOrNull
        ?: error("Unexpected meeting-note nullable string")

    else -> error("Unexpected meeting-note nullable value")
}

private fun JsonObject.integer(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: error("Unexpected meeting-note integer")

private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: error("Unexpected meeting-note array")

private val ROOT_KEYS = setOf("title", "decisions", "action_items")
private val DECISION_KEYS = setOf("summary", "sources")
private val ACTION_KEYS = setOf("description", "owner", "due_date", "sources")
private val SOURCE_KEYS = setOf("page_index", "excerpt")
