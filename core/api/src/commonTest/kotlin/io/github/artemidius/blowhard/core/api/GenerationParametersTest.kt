package io.github.artemidius.blowhard.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GenerationParametersTest {
    @Test
    fun `model defaults are represented by absent values`() {
        val parameters = GenerationParameters()

        assertNull(parameters.maxOutputTokens)
        assertNull(parameters.temperature)
    }

    @Test
    fun `supported parameter bounds are enforced`() {
        assertFailsWith<IllegalArgumentException> { GenerationParameters(maxOutputTokens = 0) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(maxOutputTokens = 1_000_001) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(temperature = -0.1) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(temperature = 0.8) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(temperature = Double.NaN) }

        assertEquals(1, GenerationParameters(maxOutputTokens = 1).maxOutputTokens)
        assertEquals(0.7, GenerationParameters(temperature = 0.7).temperature)
    }
}
