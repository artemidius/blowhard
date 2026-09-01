package io.github.artemidius.blowhard.adapters.fixture

import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/** Deterministic provider behavior used by the offline demonstration and lifecycle tests. */
public sealed interface FixtureScenario {
    /** Streams [chunks] and then completes successfully. */
    public data class Success(public val chunks: List<String>, public val delayBetweenChunks: Duration = ZERO) :
        FixtureScenario {
        init {
            require(!delayBetweenChunks.isNegative()) { "Fixture delay must not be negative" }
        }
    }

    /** Streams [chunks] and then emits a classified failure. */
    public data class Failure(
        public val chunks: List<String> = emptyList(),
        public val kind: GenerationFailureKind = GenerationFailureKind.UNEXPECTED,
        public val reference: String? = null,
        public val delayBetweenChunks: Duration = ZERO,
    ) : FixtureScenario {
        init {
            require(!delayBetweenChunks.isNegative()) { "Fixture delay must not be negative" }
        }
    }

    /** Emits a provider rate-limit failure after any [chunks]. */
    public data class RateLimited(
        public val chunks: List<String> = emptyList(),
        public val reference: String? = "fixture-rate-limit",
        public val delayBetweenChunks: Duration = ZERO,
    ) : FixtureScenario {
        init {
            require(!delayBetweenChunks.isNegative()) { "Fixture delay must not be negative" }
        }
    }

    /** Abruptly ends the provider stream after any [chunks]. */
    public data class Interrupted(
        public val chunks: List<String> = emptyList(),
        public val delayBetweenChunks: Duration = ZERO,
    ) : FixtureScenario {
        init {
            require(!delayBetweenChunks.isNegative()) { "Fixture delay must not be negative" }
        }
    }
}
