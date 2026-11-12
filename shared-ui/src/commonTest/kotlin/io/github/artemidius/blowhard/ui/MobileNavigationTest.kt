package io.github.artemidius.blowhard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MobileNavigationTest {
    @Test
    fun `mobile navigation starts in the selected conversation`() {
        val navigation = MobileNavigationState.initial()

        assertEquals(MobileRootDestination.CHATS, navigation.selectedRoot)
        assertEquals(MobileDestination.CONVERSATION, navigation.current)
    }

    @Test
    fun `selecting a root clears transient detail history`() {
        val navigation = MobileNavigationState.initial()
            .open(MobileDestination.RUN_INSPECTOR)
            .selectRoot(MobileRootDestination.DOCUMENTS)

        assertEquals(MobileRootDestination.DOCUMENTS, navigation.selectedRoot)
        assertEquals(MobileDestination.DOCUMENTS, navigation.current)
        assertEquals(listOf("documents"), navigation.savedRoutes())
    }

    @Test
    fun `back closes the current detail and remains stable at a root`() {
        val detail = MobileNavigationState.initial().open(MobileDestination.CONVERSATION_LIST)
        val root = detail.back()

        assertEquals(MobileDestination.CONVERSATION, root.current)
        assertSame(root, root.back())
    }

    @Test
    fun `details cannot cross root boundaries`() {
        val documents = MobileNavigationState.initial().selectRoot(MobileRootDestination.DOCUMENTS)

        assertFailsWith<IllegalArgumentException> {
            documents.open(MobileDestination.RUN_INSPECTOR)
        }
        assertFailsWith<IllegalArgumentException> {
            documents.open(MobileDestination.SETTINGS)
        }
    }

    @Test
    fun `valid routes survive recreation without accepting corrupt history`() {
        val note = MobileNavigationState.initial()
            .selectRoot(MobileRootDestination.DOCUMENTS)
            .open(MobileDestination.MEETING_NOTE)

        assertEquals(note, MobileNavigationState.restore(note.savedRoutes()))
        assertEquals(MobileNavigationState.initial(), MobileNavigationState.restore(listOf("unknown")))
        assertEquals(
            MobileDestination.DOCUMENTS,
            MobileNavigationState.restore(listOf("documents", "run-inspector")).current,
        )
    }
}
