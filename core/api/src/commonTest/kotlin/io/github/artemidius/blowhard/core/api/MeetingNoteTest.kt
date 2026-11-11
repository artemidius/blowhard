package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeetingNoteTest {
    @Test
    fun `calendar dates accept leap days and reject invalid values`() {
        assertEquals("2024-02-29", MeetingNoteDate("2024-02-29").value)
        assertEquals("2000-02-29", MeetingNoteDate("2000-02-29").toString())

        listOf(
            "",
            "2026-2-01",
            "0000-01-01",
            "1900-02-29",
            "2026-02-29",
            "2026-04-31",
            "2026-13-01",
            "2026-00-01",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException> { MeetingNoteDate(value) }
        }
    }

    @Test
    fun `typed content preserves explicit nulls and bounded sources`() {
        val source = MeetingNoteSourceReference(0, "Ship on Friday")
        val content = MeetingNoteContent(
            title = null,
            decisions = listOf(MeetingDecision("Ship on Friday", listOf(source))),
            actionItems = listOf(
                MeetingActionItem("Prepare release", owner = null, dueDate = null, sources = listOf(source)),
            ),
        )

        assertEquals(null, content.title)
        assertEquals(null, content.actionItems.single().owner)
        assertEquals(null, content.actionItems.single().dueDate)
    }

    @Test
    fun `typed content rejects blank and unbounded fields`() {
        val source = MeetingNoteSourceReference(0, "Evidence")

        assertFailsWith<IllegalArgumentException> { MeetingDecision(" ", listOf(source)) }
        assertFailsWith<IllegalArgumentException> { MeetingDecision("Decision", emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            MeetingDecision("Decision", List(MeetingDecision.MAX_SOURCES + 1) { source })
        }
        assertFailsWith<IllegalArgumentException> {
            MeetingNoteContent("x".repeat(MeetingNoteContent.MAX_TITLE_LENGTH + 1), emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            MeetingNoteContent(
                null,
                List(MeetingNoteContent.MAX_ITEMS + 1) {
                    MeetingDecision("D$it", listOf(source))
                },
                emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MeetingActionItem("Action", "x".repeat(MeetingActionItem.MAX_OWNER_LENGTH + 1), null, listOf(source))
        }
        assertFailsWith<IllegalArgumentException> {
            MeetingActionItem("Action", null, null, emptyList())
        }
        assertFailsWith<IllegalArgumentException> { MeetingNoteSourceReference(-1, "Evidence") }
        assertFailsWith<IllegalArgumentException> {
            MeetingNoteSourceReference(0, "x".repeat(MeetingNoteSourceReference.MAX_EXCERPT_LENGTH + 1))
        }
    }
}
