package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.domain.ToolCallId
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ToolRegistryTest {
    @Test
    fun `strict registry executes only known calls with valid complete arguments`() = runTest {
        var executions = 0
        val registry = registry { arguments ->
            executions += 1
            ToolExecutionResult.Completed("{\"echo\":$arguments}")
        }

        val completed = registry.execute(proposal("{\"query\":\"release\",\"limit\":2}"))
        assertEquals(ToolCallOutcome.SUCCEEDED, completed.outcome)
        assertEquals(1, executions)

        assertEquals(ToolCallOutcome.REJECTED, registry.execute(proposal("{\"query\":\"release\"}")).outcome)
        assertEquals(ToolCallOutcome.REJECTED, registry.execute(proposal("{\"query\":2,\"limit\":2}")).outcome)
        assertEquals(ToolCallOutcome.REJECTED, registry.execute(proposal("{\"query\":\"x\",\"limit\":2,")).outcome)
        assertEquals(1, executions)
    }

    @Test
    fun `unknown and mutating tools never reach an executor`() = runTest {
        var executions = 0
        val read = registration {
            executions += 1
            ToolExecutionResult.Completed("{}")
        }
        val write = read.copy(definition = read.definition.copy(name = ToolName("write_note"), mutating = true))
        val registry = requireNotNull(ToolRegistry.create(listOf(read, write)))

        val unknown = registry.execute(proposal("{\"query\":\"x\",\"limit\":1}", ToolName("missing")))
        val mutation = registry.execute(proposal("{\"query\":\"x\",\"limit\":1}", ToolName("write_note")))

        assertEquals(ToolCallOutcome.REJECTED, unknown.outcome)
        assertEquals(ToolCallOutcome.REJECTED, mutation.outcome)
        assertEquals(0, executions)
    }

    @Test
    fun `duplicate names and permissive or unsupported schemas are rejected at startup`() {
        val registration = registration { ToolExecutionResult.Completed("{}") }

        assertNull(ToolRegistry.create(listOf(registration, registration)))
        assertInvalidSchema(registration, "{}")
        assertInvalidSchema(registration, SCHEMA.replace("false", "true"))
        assertInvalidSchema(registration, SCHEMA.replace("false", "\"false\""))
        assertInvalidSchema(registration, SCHEMA.replace("[\"query\",\"limit\"]", "\"query\""))
    }

    @Test
    fun `executor failures are redacted while cancellation propagates`() = runTest {
        val failed = registry { error("private filesystem detail") }.execute(
            proposal("{\"query\":\"x\",\"limit\":1}"),
        )
        assertEquals(ToolCallOutcome.FAILED, failed.outcome)
        assertEquals("{\"error\":\"execution_failed\"}", failed.outputJson)

        val malformed = registry { ToolExecutionResult.Completed("not json") }.execute(
            proposal("{\"query\":\"x\",\"limit\":1}"),
        )
        assertEquals("{\"error\":\"malformed_result\"}", malformed.outputJson)

        assertFailsWith<CancellationException> {
            registry { throw CancellationException("stop") }.execute(
                proposal("{\"query\":\"x\",\"limit\":1}"),
            )
        }
    }

    private fun registry(execute: suspend (String) -> ToolExecutionResult): ToolRegistry =
        requireNotNull(ToolRegistry.create(listOf(registration(execute))))

    private fun registration(execute: suspend (String) -> ToolExecutionResult): RegisteredTool = RegisteredTool(
        ToolDefinition(ToolName("search_text"), "Search selected documents", SCHEMA),
        execute,
    )

    private fun proposal(arguments: String, name: ToolName = ToolName("search_text")): ToolCallProposal =
        ToolCallProposal(ToolCallId("call-1"), name, arguments)

    private fun assertInvalidSchema(registration: RegisteredTool, schema: String) {
        val changed = registration.copy(definition = registration.definition.copy(inputSchemaJson = schema))
        assertNull(ToolRegistry.create(listOf(changed)))
    }
}

private const val SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"},"limit":{"type":"integer"}},"required":["query","limit"]}"""
