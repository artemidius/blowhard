package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Routes an immutable attempt target without falling back between demo and live providers. */
internal class DesktopGenerationRouter(
    private val demo: TextGenerationPort,
    private val liveFactory: (String) -> OwnedGenerationPort,
) : TextGenerationPort,
    AutoCloseable {
    private val livePorts = ConcurrentHashMap<String, OwnedGenerationPort>()
    private val closed = AtomicBoolean(false)

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        if (closed.get()) {
            emit(unexpectedFailure())
            return@flow
        }
        when (val target = request.target) {
            GenerationTarget.Demo -> emitAll(demo.generate(request))
            is GenerationTarget.Live -> emitAll(liveGeneration(target.modelId, request))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        livePorts.values.forEach { port -> port.close() }
        livePorts.clear()
    }

    private fun liveGeneration(modelId: String, request: GenerationRequest): Flow<GenerationEvent> = flow {
        val port = try {
            livePorts.computeIfAbsent(modelId, liveFactory)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emit(unexpectedFailure())
            return@flow
        }
        emitAll(port.generation.generate(request))
    }
}

internal class OwnedGenerationPort(val generation: TextGenerationPort, private val closeAction: () -> Unit) :
    AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) closeAction()
    }
}

private fun unexpectedFailure(): GenerationEvent.Failed = GenerationEvent.Failed(
    GenerationFailure(GenerationFailureKind.UNEXPECTED),
)
