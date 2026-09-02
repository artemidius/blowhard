package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunTimestamp
import kotlin.time.Clock

/** Supplies UTC timestamps for run-state transitions. */
public fun interface RunClock {
    /** Returns the current UTC time. */
    public fun now(): RunTimestamp
}

/** Production clock backed by the platform system clock. */
public object SystemRunClock : RunClock {
    override fun now(): RunTimestamp = RunTimestamp(Clock.System.now().toEpochMilliseconds())
}
