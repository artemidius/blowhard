package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.GenerationEvent

internal class TextDeltaBatcher(private val emit: suspend (GenerationEvent) -> Unit) {
    private val pending = StringBuilder()
    private var acceptedCharacters = 0

    suspend fun add(text: String) {
        if (text.isEmpty()) return
        if (acceptedCharacters + text.length > MAX_GENERATED_CHARACTERS) throw InvalidMistralStreamException()
        acceptedCharacters += text.length
        pending.append(text)
        if (pending.length >= MINIMUM_DELTA_CHARACTERS) flush()
    }

    suspend fun flush() {
        if (pending.isEmpty()) return
        emit(GenerationEvent.TextDelta(pending.toString()))
        pending.clear()
    }
}

private const val MINIMUM_DELTA_CHARACTERS = 128
private const val MAX_GENERATED_CHARACTERS = 1_000_000
