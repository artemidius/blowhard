package io.github.artemidius.blowhard.core.application

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunInfrastructureTest {
    @Test
    fun `execution policy requires positive concurrency`() {
        assertFailsWith<IllegalArgumentException> {
            RunExecutionPolicy(maxConcurrentRuns = 0)
        }
    }

    @Test
    fun `sequential ID factory requires meaningful prefix`() {
        assertFailsWith<IllegalArgumentException> {
            SequentialRunIdFactory(" ")
        }
    }

    @Test
    fun `system clock returns valid epoch timestamp`() {
        assertTrue(SystemRunClock.now().epochMilliseconds > 0)
    }
}
