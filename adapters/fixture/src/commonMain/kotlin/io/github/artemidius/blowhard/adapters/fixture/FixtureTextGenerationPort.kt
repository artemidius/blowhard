package io.github.artemidius.blowhard.adapters.fixture

import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/** Offline text-generation adapter driven entirely by deterministic [FixtureScenario] values. */
public class FixtureTextGenerationPort(private val selectScenario: (GenerationRequest) -> FixtureScenario) :
    TextGenerationPort {
    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        when (val scenario = selectScenario(request)) {
            is FixtureScenario.Success -> {
                emitChunks(scenario.chunks, scenario.delayBetweenChunks)
                emit(GenerationEvent.Completed)
            }

            is FixtureScenario.Failure -> {
                emitChunks(scenario.chunks, scenario.delayBetweenChunks)
                emit(GenerationEvent.Failed(GenerationFailure(scenario.kind, scenario.reference)))
            }

            is FixtureScenario.RateLimited -> {
                emitChunks(scenario.chunks, scenario.delayBetweenChunks)
                emit(
                    GenerationEvent.Failed(
                        GenerationFailure(GenerationFailureKind.RATE_LIMITED, scenario.reference),
                    ),
                )
            }

            is FixtureScenario.Interrupted -> {
                emitChunks(scenario.chunks, scenario.delayBetweenChunks)
                throw FixtureStreamInterruptedException()
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<GenerationEvent>.emitChunks(
        chunks: List<String>,
        delayBetweenChunks: Duration,
    ) {
        chunks.forEachIndexed { index, chunk ->
            if (index > 0) delay(delayBetweenChunks)
            emit(GenerationEvent.TextDelta(chunk))
        }
    }
}

private class FixtureStreamInterruptedException : RuntimeException("Synthetic fixture interruption")
