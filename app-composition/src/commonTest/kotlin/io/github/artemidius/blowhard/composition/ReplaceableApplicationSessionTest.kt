package io.github.artemidius.blowhard.composition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReplaceableApplicationSessionTest {
    @Test
    fun `reset closes erases creates and then publishes replacement`() = runTest {
        val events = mutableListOf<String>()
        val owner = owner(
            events = events,
            create = {
                events += "create"
                "replacement"
            },
        )

        assertEquals(ApplicationSessionResetResult.RESET, owner.reset())

        assertEquals(listOf("close:initial", "erase", "create"), events)
        assertEquals("replacement", owner.session.value)
    }

    @Test
    fun `erasure failure still publishes clean replacement`() = runTest {
        val events = mutableListOf<String>()
        val owner = owner(events = events, erase = { false })

        assertEquals(ApplicationSessionResetResult.LOCAL_DATA_ERASURE_FAILED, owner.reset())

        assertEquals(listOf("close:initial", "erase"), events)
        assertEquals("replacement", owner.session.value)
    }

    @Test
    fun `concurrent reset is rejected instead of queued`() = runTest {
        val releaseErasure = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val owner = owner(
            events = events,
            erase = {
                releaseErasure.await()
                true
            },
        )

        val first = async { owner.reset() }
        runCurrent()
        assertEquals(ApplicationSessionResetResult.ALREADY_RUNNING, owner.reset())

        releaseErasure.complete(Unit)
        assertEquals(ApplicationSessionResetResult.RESET, first.await())
        assertEquals(listOf("close:initial", "erase"), events)
    }

    @Test
    fun `host close is idempotent and prevents reset`() = runTest {
        val events = mutableListOf<String>()
        val owner = owner(events)

        owner.close()
        owner.close()

        assertEquals(listOf("close:initial"), events)
        assertEquals(ApplicationSessionResetResult.OWNER_CLOSED, owner.reset())
    }

    @Test
    fun `close failure prevents unsafe erasure`() = runTest {
        var eraseCalls = 0
        val owner = ReplaceableApplicationSession(
            initialSession = "initial",
            factory = ApplicationSessionFactory { "replacement" },
            closer = ApplicationSessionCloser { error("private close detail") },
            eraser = ApplicationLocalDataEraser {
                eraseCalls += 1
                true
            },
        )

        assertEquals(ApplicationSessionResetResult.SESSION_CLOSE_FAILED, owner.reset())
        assertEquals(0, eraseCalls)
        assertEquals("initial", owner.session.value)
    }

    @Test
    fun `replacement failure closes owner after sanitized result`() = runTest {
        val owner = ReplaceableApplicationSession(
            initialSession = "initial",
            factory = ApplicationSessionFactory { error("private factory detail") },
            closer = ApplicationSessionCloser { },
            eraser = ApplicationLocalDataEraser { true },
        )

        assertEquals(ApplicationSessionResetResult.REPLACEMENT_FAILED, owner.reset())
        assertEquals(ApplicationSessionResetResult.OWNER_CLOSED, owner.reset())
    }

    @Test
    fun `cancellation cannot interrupt an accepted reset`() = runTest {
        val releaseErasure = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val owner = owner(
            events = events,
            erase = {
                releaseErasure.await()
                true
            },
        )
        val reset = async { owner.reset() }
        runCurrent()

        reset.cancel()
        releaseErasure.complete(Unit)
        reset.cancelAndJoin()

        assertEquals(listOf("close:initial", "erase"), events)
        assertEquals("replacement", owner.session.value)
    }

    private fun owner(
        events: MutableList<String>,
        create: suspend () -> String = { "replacement" },
        erase: suspend () -> Boolean = { true },
    ): ReplaceableApplicationSession<String> = ReplaceableApplicationSession(
        initialSession = "initial",
        factory = ApplicationSessionFactory { create() },
        closer = ApplicationSessionCloser { events += "close:$it" },
        eraser = ApplicationLocalDataEraser {
            events += "erase"
            erase()
        },
    )
}
