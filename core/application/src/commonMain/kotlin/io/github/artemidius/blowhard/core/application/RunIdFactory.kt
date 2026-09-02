package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Creates a fresh opaque identity for each accepted run attempt. */
public fun interface RunIdFactory {
    /** Returns an identity that has not previously been issued by this factory. */
    public suspend fun next(): RunId
}

/** In-memory ID source suitable for one application process. */
public class SequentialRunIdFactory(private val prefix: String = "run") : RunIdFactory {
    private val mutex = Mutex()
    private var sequence = 0L

    init {
        require(prefix.isNotBlank()) { "Run ID prefix must not be blank" }
    }

    override suspend fun next(): RunId = mutex.withLock {
        sequence += 1
        RunId("$prefix-$sequence")
    }
}
