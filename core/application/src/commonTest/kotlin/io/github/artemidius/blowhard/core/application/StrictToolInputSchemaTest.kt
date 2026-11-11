package io.github.artemidius.blowhard.core.application

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrictToolInputSchemaTest {
    @Test
    fun `supported scalar types and optional properties validate exactly`() {
        val schema = assertNotNull(StrictToolInputSchema.parse(SCALAR_SCHEMA))

        assertTrue(schema.accepts("{}"))
        assertTrue(schema.accepts("{\"text\":\"x\",\"count\":2,\"score\":1.5,\"enabled\":true}"))
        assertFalse(schema.accepts("{\"count\":2.5}"))
        assertFalse(schema.accepts("{\"score\":\"1.5\"}"))
        assertFalse(schema.accepts("{\"enabled\":\"true\"}"))
        assertFalse(schema.accepts("{\"unknown\":1}"))
        assertFalse(schema.accepts("[]"))
    }

    @Test
    fun `unsupported schemas fail closed`() {
        listOf(
            "not-json",
            "[]",
            "{}",
            SCALAR_SCHEMA.replace("\"properties\"", "\"unknown\""),
            SCALAR_SCHEMA.replace("\"text\"", "\"Invalid\""),
            SCALAR_SCHEMA.replace("{\"type\":\"string\"}", "[]"),
            SCALAR_SCHEMA.replace("{\"type\":\"string\"}", "{\"type\":\"array\"}"),
            SCALAR_SCHEMA.replace("{\"type\":\"string\"}", "{\"type\":\"string\",\"extra\":true}"),
            SCALAR_SCHEMA.replace("\"required\":[]", "\"required\":[\"missing\"]"),
            SCALAR_SCHEMA.replace("\"required\":[]", "\"required\":[1]"),
            SCALAR_SCHEMA.replace("\"required\":[]", "\"required\":[\"text\",\"text\"]"),
        ).forEach { assertNull(StrictToolInputSchema.parse(it), it) }
    }
}

private const val SCALAR_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"text":{"type":"string"},"count":{"type":"integer"},"score":{"type":"number"},"enabled":{"type":"boolean"}},"required":[]}"""
