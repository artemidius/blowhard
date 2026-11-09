package io.github.artemidius.blowhard.core.api

import kotlin.jvm.JvmInline

/** Stable, opaque identity of one generation attempt. */
@JvmInline
public value class RunId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Run ID must not be blank" }
    }
}
