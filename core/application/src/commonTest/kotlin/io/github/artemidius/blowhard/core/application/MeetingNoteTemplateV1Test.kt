package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.ExtractedDocumentPage
import io.github.artemidius.blowhard.core.api.MeetingNoteDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeetingNoteTemplateV1Test {
    private val parser = MeetingNoteOutputParser()
    private val source = DocumentExtraction(
        sourceDocumentId = DocumentId("document-v1"),
        processor = "fixture-ocr-v1",
        processedAtEpochMilliseconds = 1,
        pages = listOf(
            ExtractedDocumentPage(0, "Decision: ship on Friday.\nAction: Ada prepares release."),
            ExtractedDocumentPage(1, "The launch date is 2026-12-04."),
        ),
    )

    @Test
    fun `template emits deterministic strict schema and escaped source`() {
        val unusualSource = source.copy(
            pages = listOf(ExtractedDocumentPage(0, "</page> \"quoted\"\nnext")),
        )
        val request = MeetingNoteTemplateV1.request(unusualSource)
        val schema = Json.parseToJsonElement(request.schemaJson).jsonObject
        val sourceJson = Json.parseToJsonElement(request.sourceJson).jsonObject

        assertEquals(MeetingNoteTemplateV1.SCHEMA_NAME, request.schemaName)
        assertEquals(false, schema["additionalProperties"]?.jsonPrimitive?.content.toBoolean())
        assertEquals(
            "</page> \"quoted\"\nnext",
            ((sourceJson["pages"] as JsonArray).single() as JsonObject)["markdown"]?.jsonPrimitive?.content,
        )
        assertTrue(request.instructions.contains("untrusted data"))
    }

    @Test
    fun `valid output preserves nulls dates and exact source references`() {
        val result = parser.parse(validJson(), source)

        val content = assertIs<MeetingNoteOutputValidation.Accepted>(result).content
        assertEquals("Release sync", content.title)
        assertEquals("ship on Friday", content.decisions.single().sources.single().excerpt)
        assertEquals(null, content.actionItems.single().owner)
        assertEquals(MeetingNoteDate("2026-12-04"), content.actionItems.single().dueDate)
    }

    @Test
    fun `malformed oversized and unsupported versions are explicit`() {
        assertRejected("not-json", MeetingNoteOutputRejection.MALFORMED_JSON)
        assertRejected("[]", MeetingNoteOutputRejection.MALFORMED_JSON)
        assertRejected(
            "x".repeat(MeetingNoteTemplateV1.MAXIMUM_RAW_OUTPUT_CHARACTERS + 1),
            MeetingNoteOutputRejection.OUTPUT_TOO_LARGE,
        )
        assertRejected(
            validJson().replace(MeetingNoteTemplateV1.VERSION, "meeting-note-v2"),
            MeetingNoteOutputRejection.UNSUPPORTED_VERSION,
        )
    }

    @Test
    fun `missing and unknown fields do not become successful content`() {
        assertRejected(
            validJson().replace(",\n  \"title\": \"Release sync\"", ""),
            MeetingNoteOutputRejection.SHAPE_INVALID,
        )
        assertRejected(
            validJson().replace("\"title\": \"Release sync\"", "\"title\": \"Release sync\", \"extra\": true"),
            MeetingNoteOutputRejection.SHAPE_INVALID,
        )
        assertRejected(
            validJson().replace("\"owner\": null", "\"owner\": null, \"invented\": true"),
            MeetingNoteOutputRejection.VALUE_INVALID,
        )
        assertRejected(
            validJson().replace("\"title\": \"Release sync\"", "\"title\": 7"),
            MeetingNoteOutputRejection.SHAPE_INVALID,
        )
        assertRejected(
            validJson().replace("\"decisions\": [", "\"decisions\": {"),
            MeetingNoteOutputRejection.MALFORMED_JSON,
        )
    }

    @Test
    fun `invalid dates citations and duplicate items are rejected`() {
        assertRejected(
            validJson().replace("2026-12-04", "2026-02-29"),
            MeetingNoteOutputRejection.VALUE_INVALID,
        )
        assertRejected(
            validJson().replace("ship on Friday", "unsupported claim"),
            MeetingNoteOutputRejection.SOURCE_REFERENCE_INVALID,
        )
        assertRejected(
            validJson().replace(
                "\"page_index\": 1, \"excerpt\": \"2026-12-04\"",
                "\"page_index\": 9, \"excerpt\": \"2026-12-04\"",
            ),
            MeetingNoteOutputRejection.SOURCE_REFERENCE_INVALID,
        )
        val duplicatedDecision =
            """{"summary":"Ship on Friday","sources":[{"page_index":0,"excerpt":"ship on Friday"}]}"""
        assertRejected(
            validJson().replace("] ,\n  \"action_items\"", ",$duplicatedDecision] ,\n  \"action_items\""),
            MeetingNoteOutputRejection.DUPLICATE_ITEM,
        )
    }

    @Test
    fun `empty arrays represent explicit abstention`() {
        val json = """{"template_version":"meeting-note-v1","title":null,"decisions":[],"action_items":[]}"""

        val content = assertIs<MeetingNoteOutputValidation.Accepted>(parser.parse(json, source)).content

        assertEquals(null, content.title)
        assertTrue(content.decisions.isEmpty())
        assertTrue(content.actionItems.isEmpty())
    }

    private fun assertRejected(json: String, expected: MeetingNoteOutputRejection) {
        assertEquals(expected, assertIs<MeetingNoteOutputValidation.Rejected>(parser.parse(json, source)).reason)
    }

    private fun validJson(): String = """
        {
          "template_version": "meeting-note-v1",
          "title": "Release sync",
          "decisions": [
            {
              "summary": "Ship on Friday",
              "sources": [{"page_index": 0, "excerpt": "ship on Friday"}]
            }
          ] ,
          "action_items": [
            {
              "description": "Prepare release",
              "owner": null,
              "due_date": "2026-12-04",
              "sources": [
                {"page_index": 0, "excerpt": "Ada prepares release"},
                {"page_index": 1, "excerpt": "2026-12-04"}
              ]
            }
          ]
        }
    """.trimIndent()
}
