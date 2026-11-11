package io.github.artemidius.blowhard.core.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class StrictToolInputSchema(val properties: Map<String, String>, val required: Set<String>) {
    fun accepts(argumentsJson: String): Boolean = parseObject(argumentsJson)?.let { arguments ->
        arguments.keys.all(properties::containsKey) &&
            arguments.keys.containsAll(required) &&
            arguments.all { (name, value) -> value.hasType(properties.getValue(name)) }
    } ?: false

    companion object {
        fun parse(value: String): StrictToolInputSchema? {
            val schema = parseObject(value) ?: return null
            return runCatching {
                require(schema.keys.all(SUPPORTED_SCHEMA_FIELDS::contains))
                require(schema.string("type") == "object")
                require(schema.boolean("additionalProperties") == false)
                val properties = requireNotNull(schema["properties"] as? JsonObject).mapValues { (name, definition) ->
                    require(PROPERTY_NAME_PATTERN.matches(name))
                    requireNotNull(definition.propertyType())
                }
                val required = schema["required"]?.let { requireNotNull(it.asStringSet()) } ?: emptySet()
                require(properties.keys.containsAll(required))
                StrictToolInputSchema(properties, required)
            }.getOrNull()
        }
    }
}

private fun parseObject(value: String): JsonObject? = runCatching {
    TOOL_JSON.parseToJsonElement(value) as? JsonObject
}.getOrNull()

private fun JsonElement.propertyType(): String? = (this as? JsonObject)
    ?.takeIf { it.keys == setOf("type") }
    ?.string("type")
    ?.takeIf(SUPPORTED_TYPES::contains)

private fun JsonObject.string(name: String): String? = this[name]?.let { element ->
    (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)
    ?.takeUnless { it.isString }
    ?.content
    ?.toBooleanStrictOrNull()

private fun JsonElement.asStringSet(): Set<String>? = (this as? kotlinx.serialization.json.JsonArray)?.let { values ->
    val strings = values.mapNotNull { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
    strings.takeIf { it.size == values.size && it.distinct().size == it.size }?.toSet()
}

private fun JsonElement.hasType(expected: String): Boolean = when (expected) {
    "string" -> this is JsonPrimitive && isString
    "integer" -> this is JsonPrimitive && !isString && content.toLongOrNull() != null
    "number" -> this is JsonPrimitive && !isString && content.toDoubleOrNull() != null
    "boolean" -> this is JsonPrimitive && !isString && content.toBooleanStrictOrNull() != null
    else -> false
}

private val TOOL_JSON = Json { isLenient = false }
private val PROPERTY_NAME_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")
private val SUPPORTED_TYPES = setOf("string", "integer", "number", "boolean")
private val SUPPORTED_SCHEMA_FIELDS = setOf("type", "additionalProperties", "properties", "required")
