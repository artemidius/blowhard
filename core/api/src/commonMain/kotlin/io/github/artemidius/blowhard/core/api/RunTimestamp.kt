package io.github.artemidius.blowhard.core.api

import kotlin.jvm.JvmInline

/** UTC time associated with a run-state transition. */
@JvmInline
public value class RunTimestamp(public val epochMilliseconds: Long) {
    init {
        require(epochMilliseconds >= 0) { "Run timestamp must not be negative" }
    }
}
