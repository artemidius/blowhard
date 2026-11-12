package io.github.artemidius.blowhard.adapters.mistral

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

/**
 * Executes only live Mistral attempts and owns one bounded network session per collection.
 *
 * A request never falls back to a fixture provider. The selected model is copied from the
 * immutable [GenerationTarget.Live] attempt target when the flow is collected.
 */
public class MistralLiveTextGenerationPort private constructor(private val sessions: MistralGenerationSessionFactory) :
    TextGenerationPort {
    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val target = request.target as? GenerationTarget.Live
        if (target == null) {
            emit(invalidTargetFailure())
            return@flow
        }
        val session = try {
            sessions.create(target.modelId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emit(unexpectedFailure())
            return@flow
        }
        try {
            emitAll(session.generation.generate(request))
        } finally {
            runCatching(session.close)
        }
    }

    /** Creates a live-only adapter using a platform-default Mistral HTTP engine. */
    public companion object {
        /** Creates independently owned, request-bounded generation sessions. */
        public fun create(credentials: MistralCredentialProvider): MistralLiveTextGenerationPort =
            MistralLiveTextGenerationPort { modelId ->
                val generation = MistralTextGenerationPort.create(
                    credentials,
                    MistralChatConfiguration(modelId),
                )
                MistralGenerationSession(generation, generation::close)
            }

        internal fun createForTest(sessions: MistralGenerationSessionFactory): MistralLiveTextGenerationPort =
            MistralLiveTextGenerationPort(sessions)
    }
}

internal fun interface MistralGenerationSessionFactory {
    fun create(modelId: String): MistralGenerationSession
}

internal class MistralGenerationSession(val generation: TextGenerationPort, val close: () -> Unit)

private fun invalidTargetFailure(): GenerationEvent.Failed = GenerationEvent.Failed(
    GenerationFailure(GenerationFailureKind.INVALID_REQUEST),
)

private fun unexpectedFailure(): GenerationEvent.Failed = GenerationEvent.Failed(
    GenerationFailure(GenerationFailureKind.UNEXPECTED),
)
