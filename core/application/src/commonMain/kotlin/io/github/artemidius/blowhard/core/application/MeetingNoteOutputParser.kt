package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.MeetingActionItem
import io.github.artemidius.blowhard.core.api.MeetingDecision
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteDate
import io.github.artemidius.blowhard.core.api.MeetingNoteSourceReference
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal class MeetingNoteOutputParser(private val json: Json = Json) {
    fun parse(rawOutput: String, source: DocumentExtraction): MeetingNoteOutputValidation = when {
        rawOutput.length > MeetingNoteTemplateV1.MAXIMUM_RAW_OUTPUT_CHARACTERS ->
            rejected(MeetingNoteOutputRejection.OUTPUT_TOO_LARGE)

        else -> parseBounded(rawOutput, source)
    }

    private fun parseBounded(rawOutput: String, source: DocumentExtraction): MeetingNoteOutputValidation = try {
        val root = json.parseToJsonElement(rawOutput) as? JsonObject
            ?: fail(MeetingNoteOutputRejection.MALFORMED_JSON)
        MeetingNoteOutputValidation.Accepted(MeetingNoteJsonReader(source).read(root))
    } catch (_: SerializationException) {
        rejected(MeetingNoteOutputRejection.MALFORMED_JSON)
    } catch (failure: MeetingNoteParseException) {
        rejected(failure.reason)
    }
}

private class MeetingNoteJsonReader(private val source: DocumentExtraction) {
    fun read(root: JsonObject): MeetingNoteContent {
        requireKeys(root, ROOT_KEYS, MeetingNoteOutputRejection.SHAPE_INVALID)
        if (root.string("template_version") != MeetingNoteTemplateV1.VERSION) {
            fail(MeetingNoteOutputRejection.UNSUPPORTED_VERSION)
        }
        val title = root.nullableString("title", MeetingNoteOutputRejection.SHAPE_INVALID)
        val decisions = root.array("decisions").map(::decision)
        val actionItems = root.array("action_items").map(::actionItem)
        if (decisions.hasDuplicateBy(MeetingDecision::summary) ||
            actionItems.hasDuplicateBy(MeetingActionItem::description)
        ) {
            fail(MeetingNoteOutputRejection.DUPLICATE_ITEM)
        }
        return construct { MeetingNoteContent(title, decisions, actionItems) }
    }

    private fun decision(element: JsonElement): MeetingDecision {
        val value = element.objectValue()
        requireKeys(value, DECISION_KEYS)
        return construct { MeetingDecision(value.string("summary"), references(value.array("sources"))) }
    }

    private fun actionItem(element: JsonElement): MeetingActionItem {
        val value = element.objectValue()
        requireKeys(value, ACTION_KEYS)
        val owner = value.nullableString("owner")
        val date = value.nullableString("due_date")?.let { rawDate -> construct { MeetingNoteDate(rawDate) } }
        return construct {
            MeetingActionItem(
                description = value.string("description"),
                owner = owner,
                dueDate = date,
                sources = references(value.array("sources")),
            )
        }
    }

    private fun references(elements: JsonArray): List<MeetingNoteSourceReference> = elements.map { element ->
        val value = element.objectValue()
        requireKeys(value, SOURCE_KEYS)
        val pageIndex = value.integer("page_index")
        val excerpt = value.string("excerpt")
        val page = source.pages.getOrNull(pageIndex)
            ?: fail(MeetingNoteOutputRejection.SOURCE_REFERENCE_INVALID)
        if (!page.markdown.contains(excerpt)) fail(MeetingNoteOutputRejection.SOURCE_REFERENCE_INVALID)
        construct { MeetingNoteSourceReference(pageIndex, excerpt) }
    }

    private fun JsonElement.objectValue(): JsonObject =
        this as? JsonObject ?: fail(MeetingNoteOutputRejection.VALUE_INVALID)

    private fun JsonObject.string(key: String): String {
        val value = this[key] as? JsonPrimitive ?: fail(MeetingNoteOutputRejection.VALUE_INVALID)
        return value.takeIf(JsonPrimitive::isString)?.contentOrNull
            ?: fail(MeetingNoteOutputRejection.VALUE_INVALID)
    }

    private fun JsonObject.integer(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: fail(MeetingNoteOutputRejection.VALUE_INVALID)

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: fail(MeetingNoteOutputRejection.VALUE_INVALID)

    private fun JsonObject.nullableString(
        key: String,
        rejection: MeetingNoteOutputRejection = MeetingNoteOutputRejection.VALUE_INVALID,
    ): String? = when (val value = this[key]) {
        JsonNull -> null
        is JsonPrimitive -> value.takeIf(JsonPrimitive::isString)?.contentOrNull ?: fail(rejection)
        else -> fail(rejection)
    }

    private fun requireKeys(
        value: JsonObject,
        expected: Set<String>,
        rejection: MeetingNoteOutputRejection = MeetingNoteOutputRejection.VALUE_INVALID,
    ) {
        if (value.keys != expected) fail(rejection)
    }

    private companion object {
        val ROOT_KEYS = setOf("template_version", "title", "decisions", "action_items")
        val DECISION_KEYS = setOf("summary", "sources")
        val ACTION_KEYS = setOf("description", "owner", "due_date", "sources")
        val SOURCE_KEYS = setOf("page_index", "excerpt")
    }
}

private fun <T> construct(block: () -> T): T = try {
    block()
} catch (_: IllegalArgumentException) {
    fail(MeetingNoteOutputRejection.VALUE_INVALID)
}

private fun <T> List<T>.hasDuplicateBy(selector: (T) -> String): Boolean {
    val identities = map { selector(it).trim().lowercase() }
    return identities.size != identities.toSet().size
}

internal sealed interface MeetingNoteOutputValidation {
    data class Accepted(val content: MeetingNoteContent) : MeetingNoteOutputValidation

    data class Rejected(val reason: MeetingNoteOutputRejection) : MeetingNoteOutputValidation
}

internal enum class MeetingNoteOutputRejection {
    MALFORMED_JSON,
    OUTPUT_TOO_LARGE,
    UNSUPPORTED_VERSION,
    SHAPE_INVALID,
    VALUE_INVALID,
    SOURCE_REFERENCE_INVALID,
    DUPLICATE_ITEM,
}

private class MeetingNoteParseException(val reason: MeetingNoteOutputRejection) : Exception()

private fun fail(reason: MeetingNoteOutputRejection): Nothing = throw MeetingNoteParseException(reason)

private fun rejected(reason: MeetingNoteOutputRejection): MeetingNoteOutputValidation =
    MeetingNoteOutputValidation.Rejected(reason)
