package io.github.artemidius.blowhard.core.application

/** Bounds durable writes while a generation stream is active. */
public data class RunCheckpointPolicy(
    public val minimumOutputCharacters: Int = DEFAULT_MINIMUM_OUTPUT_CHARACTERS,
    public val maximumIntervalMilliseconds: Long = DEFAULT_MAXIMUM_INTERVAL_MILLISECONDS,
) {
    init {
        require(minimumOutputCharacters > 0) { "Checkpoint output threshold must be positive" }
        require(maximumIntervalMilliseconds > 0) { "Checkpoint interval must be positive" }
    }

    private companion object {
        private const val DEFAULT_MINIMUM_OUTPUT_CHARACTERS: Int = 512
        private const val DEFAULT_MAXIMUM_INTERVAL_MILLISECONDS: Long = 2_000
    }
}
