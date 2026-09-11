package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ModelCatalogPortTest {
    @Test
    fun `model identity and context bounds are enforced`() {
        assertFailsWith<IllegalArgumentException> { model(id = " ") }
        assertFailsWith<IllegalArgumentException> { model(contextWindow = 0) }
        assertFailsWith<IllegalArgumentException> { model(contextWindow = -1) }
    }

    @Test
    fun `retry delay cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            ModelCatalogFailure(ModelCatalogFailureKind.RATE_LIMITED, retryAfterSeconds = -1)
        }
    }

    private fun model(id: String = "model", contextWindow: Long? = null): AvailableModel = AvailableModel(
        id = id,
        aliases = emptyList(),
        contextWindowTokens = contextWindow,
        availability = ModelAvailability.UNKNOWN,
        capabilities = ModelCapabilities(
            chatCompletion = CapabilitySupport.UNKNOWN,
            fillInMiddle = CapabilitySupport.UNKNOWN,
            functionCalling = CapabilitySupport.UNKNOWN,
            fineTuning = CapabilitySupport.UNKNOWN,
            vision = CapabilitySupport.UNKNOWN,
            classification = CapabilitySupport.UNKNOWN,
        ),
    )
}
