package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolModelPort
import io.github.artemidius.blowhard.core.domain.ToolModelResponse
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoundedToolOrchestratorTest {
    @Test
    fun `deterministic multi-step trace preserves call identity and order`() = runTest {
        val responses = ArrayDeque(
            listOf(
                ToolModelResponse.Calls(listOf(call("one"), call("two"))),
                ToolModelResponse.Text("Grounded answer"),
            ),
        )
        val executed = mutableListOf<String>()
        val states = mutableListOf<ToolOrchestrationState>()
        val orchestrator = orchestrator(ToolModelPort { responses.removeFirst() }) { arguments ->
            executed += arguments
            ToolExecutionResult.Completed("{\"matches\":[]}")
        }

        val terminal = orchestrator.run(RUN_ID, "Find release notes", states::add)

        assertEquals(listOf("{\"query\":\"one\"}", "{\"query\":\"two\"}"), executed)
        val trace = assertIs<ToolOrchestrationState.Succeeded>(terminal).trace
        assertEquals(
            listOf("call-one", "call-two"),
            trace.filterIsInstance<ToolTraceEvent.CallCompleted>().map {
                it.id.value
            },
        )
        assertIs<ToolOrchestrationState.Waiting>(states.first())
        assertIs<ToolOrchestrationState.Succeeded>(states.last())
        assertEquals(listOf(1, 2), states.filterIsInstance<ToolOrchestrationState.Running>().map { it.step })
    }

    @Test
    fun `unknown and invalid calls return typed results without unregistered execution`() = runTest {
        val observedResults = mutableListOf<ToolCallOutcome>()
        var executions = 0
        var turn = 0
        val model = ToolModelPort { request ->
            if (turn++ == 0) {
                ToolModelResponse.Calls(
                    listOf(
                        call("missing", ToolName("unknown")),
                        ToolCallProposal(ToolCallId("invalid"), ToolName("search_text"), "{"),
                    ),
                )
            } else {
                observedResults += request.messages.last()
                    .let { it as io.github.artemidius.blowhard.core.domain.ToolModelMessage.ToolResults }
                    .results.map { it.outcome }
                ToolModelResponse.Text("Handled safely")
            }
        }
        val terminal = orchestrator(model) {
            executions += 1
            ToolExecutionResult.Completed("{}")
        }
            .run(RUN_ID, "Try tools")

        assertIs<ToolOrchestrationState.Succeeded>(terminal)
        assertEquals(listOf(ToolCallOutcome.REJECTED, ToolCallOutcome.REJECTED), observedResults)
        assertEquals(0, executions)
    }

    @Test
    fun `mixed empty duplicate and excessive provider calls fail closed`() = runTest {
        val cases = listOf(
            ToolModelResponse.Calls(emptyList()) to ToolOrchestrationFailure.INVALID_PROVIDER_RESPONSE,
            ToolModelResponse.Calls(listOf(call("one")), "also text") to
                ToolOrchestrationFailure.INVALID_PROVIDER_RESPONSE,
            ToolModelResponse.Calls(listOf(call("same"), call("same"))) to
                ToolOrchestrationFailure.DUPLICATE_CALL_ID,
            ToolModelResponse.Calls(listOf(call("1"), call("2"), call("3"))) to
                ToolOrchestrationFailure.CALL_LIMIT_EXCEEDED,
        )
        cases.forEach { (response, expected) ->
            val terminal = orchestrator(
                ToolModelPort { response },
                policy = ToolOrchestrationPolicy(maximumCallsPerStep = 2),
            ).run(RUN_ID, "Prompt")
            assertEquals(expected, assertIs<ToolOrchestrationState.Failed>(terminal).reason)
        }
    }

    @Test
    fun `step exhaustion timeout and cancellation are terminally distinct`() = runTest {
        var callIndex = 0
        val exhausted = orchestrator(
            ToolModelPort { ToolModelResponse.Calls(listOf(call("${callIndex++}"))) },
            policy = ToolOrchestrationPolicy(maximumSteps = 2),
        ).run(RUN_ID, "Prompt")
        assertEquals(
            ToolOrchestrationFailure.STEP_LIMIT_EXCEEDED,
            assertIs<ToolOrchestrationState.Failed>(exhausted).reason,
        )

        val timedOut = orchestrator(
            ToolModelPort {
                delay(100)
                ToolModelResponse.Text("late")
            },
            policy = ToolOrchestrationPolicy(timeoutMilliseconds = 1),
        ).run(RUN_ID, "Prompt")
        assertEquals(ToolOrchestrationFailure.TIMED_OUT, assertIs<ToolOrchestrationState.Failed>(timedOut).reason)

        val cancelled = orchestrator(ToolModelPort { throw CancellationException("stop") })
        assertFailsWith<CancellationException> { cancelled.run(RUN_ID, "Prompt") }
    }

    @Test
    fun `blank oversized and provider failures never become successful answers`() = runTest {
        val responses = listOf(
            ToolModelResponse.Text(" "),
            ToolModelResponse.Text("too long"),
            ToolModelResponse.Failed(io.github.artemidius.blowhard.core.domain.ToolModelFailureKind.AUTHENTICATION),
        )
        responses.forEach { response ->
            val terminal = orchestrator(
                ToolModelPort { response },
                policy = ToolOrchestrationPolicy(maximumAnswerCharacters = 4),
            ).run(RUN_ID, "Prompt")
            assertTrue(terminal is ToolOrchestrationState.Failed)
        }
    }

    private fun orchestrator(
        model: ToolModelPort,
        policy: ToolOrchestrationPolicy = ToolOrchestrationPolicy(),
        execute: suspend (String) -> ToolExecutionResult = { ToolExecutionResult.Completed("{}") },
    ): BoundedToolOrchestrator {
        val registration = RegisteredTool(
            ToolDefinition(ToolName("search_text"), "Search selected text", SEARCH_SCHEMA),
            execute,
        )
        return BoundedToolOrchestrator(model, requireNotNull(ToolRegistry.create(listOf(registration))), policy)
    }

    private fun call(value: String, name: ToolName = ToolName("search_text")): ToolCallProposal =
        ToolCallProposal(ToolCallId("call-$value"), name, "{\"query\":\"$value\"}")
}

private val RUN_ID = RunId("tool-run")
private const val SEARCH_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"}},"required":["query"]}"""
