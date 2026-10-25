package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.domain.MAX_TOOL_OUTPUT_CHARACTERS
import io.github.artemidius.blowhard.core.domain.ToolCallOutcome
import io.github.artemidius.blowhard.core.domain.ToolCallProposal
import io.github.artemidius.blowhard.core.domain.ToolCallResult
import io.github.artemidius.blowhard.core.domain.ToolDefinition
import io.github.artemidius.blowhard.core.domain.ToolExecutionResult
import io.github.artemidius.blowhard.core.domain.ToolExecutor
import io.github.artemidius.blowhard.core.domain.ToolName
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** One immutable, schema-checked tool registration. */
public data class RegisteredTool(public val definition: ToolDefinition, public val executor: ToolExecutor)

/** Fixed allowlist that validates complete arguments before invoking any executor. */
public class ToolRegistry private constructor(private val tools: Map<ToolName, RegistryEntry>) {
    /** Provider definitions in stable registration order. */
    public val definitions: List<ToolDefinition> = tools.values.map(RegistryEntry::definition)

    /** Resolves and executes one complete proposal without permitting mutating tools. */
    public suspend fun execute(proposal: ToolCallProposal): ToolCallResult {
        val entry = tools[proposal.name]
        return when {
            entry == null -> proposal.result(ToolCallOutcome.REJECTED, UNKNOWN_TOOL_OUTPUT)

            entry.definition.mutating -> proposal.result(ToolCallOutcome.REJECTED, MUTATION_DISABLED_OUTPUT)

            !entry.schema.accepts(proposal.argumentsJson) ->
                proposal.result(ToolCallOutcome.REJECTED, INVALID_ARGUMENTS_OUTPUT)

            else -> executeSafely(entry, proposal)
        }
    }

    /** Creates a registry only when every definition has a supported strict object schema. */
    public companion object {
        /** Returns null for an empty, duplicate, or unsupported registration set. */
        public fun create(registrations: List<RegisteredTool>): ToolRegistry? {
            val unique = registrations.takeIf {
                it.isNotEmpty() && it.map { tool -> tool.definition.name }.distinct().size == it.size
            }
            val entries = unique?.mapNotNull(RegisteredTool::toEntry)
                ?.takeIf { it.size == registrations.size }
            return entries?.associateBy { it.definition.name }?.let(::ToolRegistry)
        }
    }
}

private suspend fun executeSafely(entry: RegistryEntry, proposal: ToolCallProposal): ToolCallResult {
    val executed = try {
        entry.executor.execute(proposal.argumentsJson)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ToolExecutionResult.Failed(EXECUTION_FAILED_OUTPUT)
    }
    return executed.toResult(proposal)
}

private fun RegisteredTool.toEntry(): RegistryEntry? = StrictToolInputSchema.parse(definition.inputSchemaJson)
    ?.let { schema -> RegistryEntry(definition, executor, schema) }

private data class RegistryEntry(
    val definition: ToolDefinition,
    val executor: ToolExecutor,
    val schema: StrictToolInputSchema,
)

private fun ToolExecutionResult.toResult(proposal: ToolCallProposal): ToolCallResult {
    val (outcome, output) = when (this) {
        is ToolExecutionResult.Completed -> ToolCallOutcome.SUCCEEDED to outputJson
        is ToolExecutionResult.Rejected -> ToolCallOutcome.REJECTED to outputJson
        is ToolExecutionResult.Failed -> ToolCallOutcome.FAILED to outputJson
    }
    return if (isValidOutput(output)) {
        proposal.result(outcome, output)
    } else {
        proposal.result(ToolCallOutcome.FAILED, MALFORMED_RESULT_OUTPUT)
    }
}

private fun ToolCallProposal.result(outcome: ToolCallOutcome, output: String): ToolCallResult =
    ToolCallResult(id, name, outcome, output)

private fun isValidOutput(value: String): Boolean =
    value.isNotBlank() && value.length <= MAX_TOOL_OUTPUT_CHARACTERS && runCatching {
        JSON.parseToJsonElement(value)
    }.isSuccess

private val JSON = Json { isLenient = false }
private const val UNKNOWN_TOOL_OUTPUT = "{\"error\":\"unknown_tool\"}"
private const val MUTATION_DISABLED_OUTPUT = "{\"error\":\"mutation_disabled\"}"
private const val INVALID_ARGUMENTS_OUTPUT = "{\"error\":\"invalid_arguments\"}"
private const val EXECUTION_FAILED_OUTPUT = "{\"error\":\"execution_failed\"}"
private const val MALFORMED_RESULT_OUTPUT = "{\"error\":\"malformed_result\"}"
