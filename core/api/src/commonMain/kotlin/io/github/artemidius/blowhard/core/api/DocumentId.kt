package io.github.artemidius.blowhard.core.api

import kotlin.jvm.JvmInline

/** Stable, opaque identity of one locally imported document. */
@JvmInline
public value class DocumentId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Document ID must not be blank" }
    }
}
