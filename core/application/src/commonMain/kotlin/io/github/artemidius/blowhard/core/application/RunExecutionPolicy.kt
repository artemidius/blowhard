package io.github.artemidius.blowhard.core.application

/** Resource limits applied by the run lifecycle. */
public data class RunExecutionPolicy(public val maxConcurrentRuns: Int = DEFAULT_MAX_CONCURRENT_RUNS) {
    init {
        require(maxConcurrentRuns > 0) { "Maximum concurrent runs must be positive" }
    }

    private companion object {
        private const val DEFAULT_MAX_CONCURRENT_RUNS = 2
    }
}
