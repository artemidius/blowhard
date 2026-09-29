package io.github.artemidius.blowhard.core.api

import kotlin.jvm.JvmInline

/** Stable, opaque identity of one locally stored conversation message. */
@JvmInline
public value class MessageId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Message ID must not be blank" }
    }
}
