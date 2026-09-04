package io.github.artemidius.blowhard.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationLayoutTest {
    @Test
    fun `compact and wide layouts meet at an explicit density independent boundary`() {
        assertEquals(ConversationLayout.COMPACT, conversationLayoutFor(320.dp))
        assertEquals(ConversationLayout.COMPACT, conversationLayoutFor(759.dp))
        assertEquals(ConversationLayout.WIDE, conversationLayoutFor(760.dp))
        assertEquals(ConversationLayout.WIDE, conversationLayoutFor(3840.dp))
    }
}
