package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolCallResult
import io.github.artemidius.blowhard.core.domain.ToolModelFailureKind
import io.github.artemidius.blowhard.core.domain.ToolModelMessage
import io.github.artemidius.blowhard.core.domain.ToolModelPort
import io.github.artemidius.blowhard.core.domain.ToolModelRequest
import io.github.artemidius.blowhard.core.domain.ToolModelResponse
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Hard ceilings for one application-owned orchestration loop. */
public data class ToolOrchestrationPolicy(
    public val maximumSteps: Int = DEFAULT_MAXIMUM_STEPS,
    public val maximumTotalCalls: Int = DEFAULT_MAXIMUM_TOTAL_CALLS,
    public val maximumCallsPerStep: Int = DEFAULT_MAXIMUM_CALLS_PER_STEP,
    public val timeoutMilliseconds: Long = DEFAULT_TIMEOUT_MILLISECONDS,
    public val maximumAnswerCharacters: Int = DEFAULT_MAXIMUM_ANSWER_CHARACTERS,
) {
    init {
        require(maximumSteps in 1..MAXIMUM_STEPS_LIMIT) { "Tool step limit is invalid" }
        require(maximumTotalCalls in 1..MAXIMUM_TOTAL_CALLS_LIMIT) { "Tool call limit is invalid" }
        require(
            maximumCallsPerStep in 1..MAXIMUM_CALLS_PER_STEP_LIMIT &&
                maximumCallsPerStep <= maximumTotalCalls,
        ) {
            "Tool per-step limit is invalid"
        }
        require(timeoutMilliseconds in 1..MAXIMUM_TIMEOUT_MILLISECONDS) { "Tool timeout is invalid" }
        require(maximumAnswerCharacters in 1..MAXIMUM_ANSWER_CHARACTERS_LIMIT) { "Tool answer limit is invalid" }
    }
}

/** Observable state transition for one bounded orchestration run. */
public sealed interface ToolOrchestrationState {
    public val runId: RunId
    public val trace: List<ToolTraceEvent>

    /** Accepted but not yet sent to the provider. */
    public data class Waiting(override val runId: RunId, override val trace: List<ToolTraceEvent> = emptyList()) :
        ToolOrchestrationState

    /** Provider or sequential tool work is active. */
    public data class Running(
        override val runId: RunId,
        public val step: Int,
        public val completedCalls: Int,
        override val trace: List<ToolTraceEvent>,
    ) : ToolOrchestrationState

    /** A bounded final answer completed the run. */
    public data class Succeeded(
        override val runId: RunId,
        public val answer: String,
        override val trace: List<ToolTraceEvent>,
    ) : ToolOrchestrationState

    /** The run terminated without an answer. */
    public data class Failed(
        override val runId: RunId,
        public val reason: ToolOrchestrationFailure,
        override val trace: List<ToolTraceEvent>,
    ) : ToolOrchestrationState
}

/** Inspectable, bounded state-machine evidence. */
public sealed interface ToolTraceEvent {
    /** A provider turn began. */
    public data class ProviderTurn(public val step: Int) : ToolTraceEvent

    /** One validated call completed sequentially. */
    public data class CallCompleted(
        public val step: Int,
        public val id: ToolCallId,
        public val name: ToolName,
        public val outcome: ToolCallOutcome,
    ) : ToolTraceEvent
}

/** Content-safe terminal failures for tool orchestration. */
public enum class ToolOrchestrationFailure {
    AUTHENTICATION,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    INVALID_PROVIDER_RESPONSE,
    DUPLICATE_CALL_ID,
    CALL_LIMIT_EXCEEDED,
    STEP_LIMIT_EXCEEDED,
    TIMED_OUT,
}

/** Executes provider proposals through one fixed registry under hard limits. */
public class BoundedToolOrchestrator(
    private val model: ToolModelPort,
    private val registry: ToolRegistry,
    private val policy: ToolOrchestrationPolicy = ToolOrchestrationPolicy(),
) {
    /** Runs one prompt and emits waiting, running, and terminal states in order. */
    public suspend fun run(
        runId: RunId,
        prompt: String,
        observe: suspend (ToolOrchestrationState) -> Unit = {},
    ): ToolOrchestrationState {
        require(prompt.isNotBlank() && prompt.length <= MAXIMUM_TOOL_PROMPT_CHARACTERS) { "Tool prompt is invalid" }
        observe(ToolOrchestrationState.Waiting(runId))
        return try {
            withTimeout(policy.timeoutMilliseconds) { execute(runId, prompt, observe) }
        } catch (_: TimeoutCancellationException) {
            ToolOrchestrationState.Failed(runId, ToolOrchestrationFailure.TIMED_OUT, emptyList()).also {
                observe(it)
            }
        }
    }

    private suspend fun execute(
        runId: RunId,
        prompt: String,
        observe: suspend (ToolOrchestrationState) -> Unit,
    ): ToolOrchestrationState {
        val context = ToolRunContext(mutableListOf(ToolModelMessage.User(prompt)))
        repeat(policy.maximumSteps) { index ->
            val step = index + 1
            context.trace += ToolTraceEvent.ProviderTurn(step)
            observe(context.running(runId, step))
            val response = modelNext(context)
            val terminal = when (response) {
                is ToolModelResponse.Text -> validateAnswer(runId, response, context)
                is ToolModelResponse.Calls -> handleCalls(runId, step, response, context)
                is ToolModelResponse.Failed -> context.failed(runId, response.kind.toFailure())
            }
            if (terminal != null) return terminal.also { observe(it) }
        }
        return context.failed(runId, ToolOrchestrationFailure.STEP_LIMIT_EXCEEDED).also { observe(it) }
    }

    private suspend fun modelNext(context: ToolRunContext): ToolModelResponse = try {
        model.next(ToolModelRequest(registry.definitions, context.messages.toList()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ToolModelResponse.Failed(ToolModelFailureKind.UNAVAILABLE)
    }

    private fun validateAnswer(
        runId: RunId,
        response: ToolModelResponse.Text,
        context: ToolRunContext,
    ): ToolOrchestrationState = if (response.text.isNotBlank() &&
        response.text.length <= policy.maximumAnswerCharacters
    ) {
        ToolOrchestrationState.Succeeded(runId, response.text, context.trace.toList())
    } else {
        context.failed(runId, ToolOrchestrationFailure.INVALID_PROVIDER_RESPONSE)
    }

    private suspend fun handleCalls(
        runId: RunId,
        step: Int,
        response: ToolModelResponse.Calls,
        context: ToolRunContext,
    ): ToolOrchestrationState? {
        val failure = validateCalls(response, context)
        if (failure != null) return context.failed(runId, failure)
        val results = response.calls.map { call ->
            registry.execute(call).also { result ->
                context.seenCallIds += call.id
                context.completedCalls += 1
                context.trace += ToolTraceEvent.CallCompleted(step, call.id, call.name, result.outcome)
            }
        }
        context.messages += ToolModelMessage.AssistantCalls(response.calls)
        context.messages += ToolModelMessage.ToolResults(results)
        return null
    }

    private fun validateCalls(response: ToolModelResponse.Calls, context: ToolRunContext): ToolOrchestrationFailure? =
        when {
            !response.accompanyingText.isNullOrBlank() || response.calls.isEmpty() ->
                ToolOrchestrationFailure.INVALID_PROVIDER_RESPONSE

            response.calls.size > policy.maximumCallsPerStep ||
                context.completedCalls + response.calls.size > policy.maximumTotalCalls ->
                ToolOrchestrationFailure.CALL_LIMIT_EXCEEDED

            response.calls.map(ToolCallProposal::id).distinct().size != response.calls.size ||
                response.calls.any { it.id in context.seenCallIds } ->
                ToolOrchestrationFailure.DUPLICATE_CALL_ID

            else -> null
        }
}

private data class ToolRunContext(
    val messages: MutableList<ToolModelMessage>,
    val seenCallIds: MutableSet<ToolCallId> = mutableSetOf(),
    val trace: MutableList<ToolTraceEvent> = mutableListOf(),
    var completedCalls: Int = 0,
) {
    fun running(runId: RunId, step: Int): ToolOrchestrationState =
        ToolOrchestrationState.Running(runId, step, completedCalls, trace.toList())

    fun failed(runId: RunId, reason: ToolOrchestrationFailure): ToolOrchestrationState =
        ToolOrchestrationState.Failed(runId, reason, trace.toList())
}

private fun ToolModelFailureKind.toFailure(): ToolOrchestrationFailure = when (this) {
    ToolModelFailureKind.AUTHENTICATION -> ToolOrchestrationFailure.AUTHENTICATION
    ToolModelFailureKind.RATE_LIMITED -> ToolOrchestrationFailure.RATE_LIMITED
    ToolModelFailureKind.UNAVAILABLE -> ToolOrchestrationFailure.PROVIDER_UNAVAILABLE
    ToolModelFailureKind.INVALID_RESPONSE -> ToolOrchestrationFailure.INVALID_PROVIDER_RESPONSE
}

private const val MAXIMUM_TOOL_PROMPT_CHARACTERS = 64 * 1_024
private const val DEFAULT_MAXIMUM_STEPS = 6
private const val DEFAULT_MAXIMUM_TOTAL_CALLS = 8
private const val DEFAULT_MAXIMUM_CALLS_PER_STEP = 4
private const val DEFAULT_TIMEOUT_MILLISECONDS = 60_000L
private const val DEFAULT_MAXIMUM_ANSWER_CHARACTERS = 64 * 1_024
private const val MAXIMUM_STEPS_LIMIT = 16
private const val MAXIMUM_TOTAL_CALLS_LIMIT = 32
private const val MAXIMUM_CALLS_PER_STEP_LIMIT = 8
private const val MAXIMUM_TIMEOUT_MILLISECONDS = 300_000L
private const val MAXIMUM_ANSWER_CHARACTERS_LIMIT = 256 * 1_024
