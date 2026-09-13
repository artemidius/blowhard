package io.github.artemidius.blowhard.adapters.mistral

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLineStrict

internal enum class SseReadResult {
    DONE,
    END_OF_INPUT,
}

internal suspend fun ByteReadChannel.readMistralSse(onData: suspend (String) -> Boolean): SseReadResult {
    val frames = SseFrameBuilder()
    while (true) {
        val line = readLineStrict(MAX_SSE_LINE_CHARACTERS) ?: return SseReadResult.END_OF_INPUT
        val frame = frames.accept(line)
        if (frame is SseFrame.Data && (frame.value == DONE_SENTINEL || !onData(frame.value))) {
            return SseReadResult.DONE
        }
    }
}

internal class InvalidMistralStreamException : RuntimeException("Invalid provider stream")

private class SseFrameBuilder {
    private val dataLines = mutableListOf<String>()
    private var eventCharacters = 0

    fun accept(line: String): SseFrame = when {
        line.isEmpty() -> dispatch()
        line.startsWith(':') -> SseFrame.None
        else -> appendDataField(line)
    }

    private fun appendDataField(line: String): SseFrame {
        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        if (field == "data") {
            val rawValue = if (separator < 0) "" else line.substring(separator + 1)
            val value = rawValue.removePrefix(" ")
            eventCharacters += value.length
            if (eventCharacters > MAX_SSE_EVENT_CHARACTERS) throw InvalidMistralStreamException()
            dataLines += value
        }
        return SseFrame.None
    }

    private fun dispatch(): SseFrame {
        if (dataLines.isEmpty()) return SseFrame.None
        val data = dataLines.joinToString(separator = "\n")
        dataLines.clear()
        eventCharacters = 0
        return SseFrame.Data(data)
    }
}

private sealed interface SseFrame {
    data object None : SseFrame

    data class Data(val value: String) : SseFrame
}

private const val DONE_SENTINEL = "[DONE]"
private const val MAX_SSE_LINE_CHARACTERS = 131_072L
private const val MAX_SSE_EVENT_CHARACTERS = 262_144
