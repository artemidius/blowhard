package io.github.artemidius.blowhard.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun `packaging smoke test requires explicit opt in`() {
        assertFalse(isPackagingSmokeTest(emptyMap()))
        assertFalse(isPackagingSmokeTest(mapOf("BLOWHARD_PACKAGING_SMOKE_TEST" to "true")))
        assertTrue(isPackagingSmokeTest(mapOf("BLOWHARD_PACKAGING_SMOKE_TEST" to "1")))
    }
}
