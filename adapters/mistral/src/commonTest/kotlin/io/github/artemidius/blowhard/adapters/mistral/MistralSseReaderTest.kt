package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MistralSseReaderTest {
    @Test
    fun `fragmented UTF-8 comments and multiline data are decoded`() = runTest {
        val payload = ": heartbeat\r\ndata: {\"value\":\r\ndata: \"😀\"}\r\n\r\ndata: [DONE]\r\n\r\n"
            .encodeToByteArray()
        val emojiStart = payload.indexOfFirst { it.toInt() and 0xFF == 0xF0 }
        val channel = ByteChannel(autoFlush = true)
        backgroundScope.launch {
            channel.writeFully(payload, 0, emojiStart + 2)
            channel.writeFully(payload, emojiStart + 2, payload.size)
            channel.close()
        }
        val events = mutableListOf<String>()

        val result = channel.readMistralSse {
            events += it
            true
        }

        assertEquals(SseReadResult.DONE, result)
        assertEquals(listOf("{\"value\":\n\"😀\"}"), events)
    }

    @Test
    fun `unrelated fields are ignored and an empty data field can stop consumption`() = runTest {
        val channel = ByteChannel(autoFlush = true)
        backgroundScope.launch {
            channel.writeFully("id: 1\nevent: message\ndata\n\n".encodeToByteArray())
            channel.close()
        }
        val events = mutableListOf<String>()

        val result = channel.readMistralSse {
            events += it
            false
        }

        assertEquals(SseReadResult.DONE, result)
        assertEquals(listOf(""), events)
    }

    @Test
    fun `oversized event is rejected before it reaches JSON decoding`() = runTest {
        val line = "data: ${"x".repeat(131_073)}\n\n"
        val channel = ByteChannel(autoFlush = true)
        backgroundScope.launch {
            channel.writeFully(line.encodeToByteArray())
            channel.close()
        }

        assertFailsWith<Exception> { channel.readMistralSse { true } }
    }

    @Test
    fun `generated output is bounded before another delta is emitted`() = runTest {
        val events = mutableListOf<GenerationEvent>()
        val batcher = TextDeltaBatcher { events += it }
        batcher.add("x".repeat(1_000_000))

        assertFailsWith<InvalidMistralStreamException> { batcher.add("x") }
        assertEquals(1, events.size)
    }
}
