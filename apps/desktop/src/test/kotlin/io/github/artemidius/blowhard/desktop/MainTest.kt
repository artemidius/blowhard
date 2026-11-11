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

    @Test
    fun `live smoke test requires explicit opt in`() {
        assertFalse(isReferenceLiveSmoke(emptyMap()))
        assertFalse(isReferenceLiveSmoke(mapOf("BLOWHARD_REFERENCE_LIVE_SMOKE" to "true")))
        assertTrue(isReferenceLiveSmoke(mapOf("BLOWHARD_REFERENCE_LIVE_SMOKE" to "1")))
    }
}
