package io.github.artemidius.blowhard.core.api

import kotlin.jvm.JvmInline

/** Stable, opaque identity of a conversation. */
@JvmInline
public value class ConversationId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Conversation ID must not be blank" }
    }
}
