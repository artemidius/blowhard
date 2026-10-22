package io.github.artemidius.blowhard.core.domain

import kotlin.jvm.JvmInline

/** Stable provider identity for one proposed tool call. */
@JvmInline
public value class ToolCallId(public val value: String) {
    init {
        require(value.isNotBlank() && value.length <= MAX_TOOL_CALL_ID_CHARACTERS && value.none(Char::isISOControl)) {
            "Tool-call identity is invalid"
        }
    }
}

/** Registry-safe tool name shared with the provider schema. */
@JvmInline
public value class ToolName(public val value: String) {
    init {
        require(TOOL_NAME_PATTERN.matches(value)) { "Tool name is invalid" }
    }
}

/** Provider-neutral declaration of one application-owned tool. */
public data class ToolDefinition(
    public val name: ToolName,
    public val description: String,
    public val inputSchemaJson: String,
    public val mutating: Boolean = false,
) {
    init {
        require(description.isNotBlank() && description.length <= MAX_TOOL_DESCRIPTION_CHARACTERS) {
            "Tool description is invalid"
        }
        require(inputSchemaJson.isNotBlank() && inputSchemaJson.length <= MAX_TOOL_JSON_CHARACTERS) {
            "Tool schema is invalid"
        }
    }
}

/** One complete provider proposal; fragmented arguments never cross this boundary. */
public data class ToolCallProposal(
    public val id: ToolCallId,
    public val name: ToolName,
    public val argumentsJson: String,
) {
    init {
        require(argumentsJson.isNotBlank() && argumentsJson.length <= MAX_TOOL_JSON_CHARACTERS) {
            "Tool-call arguments are invalid"
        }
    }
}

/** Bounded result returned to the model after application-owned execution. */
public data class ToolCallResult(
    public val id: ToolCallId,
    public val name: ToolName,
    public val outcome: ToolCallOutcome,
    public val outputJson: String,
) {
    init {
        require(outputJson.isNotBlank() && outputJson.length <= MAX_TOOL_OUTPUT_CHARACTERS) {
            "Tool-call output is invalid"
        }
    }
}

/** Safe result categories exposed to provider orchestration. */
public enum class ToolCallOutcome {
    SUCCEEDED,
    REJECTED,
    FAILED,
}

/** Application-owned executor registered under exactly one immutable definition. */
public fun interface ToolExecutor {
    /** Executes already validated arguments and returns bounded JSON. */
    public suspend fun execute(argumentsJson: String): ToolExecutionResult
}

/** Safe executor outcome; raw infrastructure failures stay behind the executor boundary. */
public sealed interface ToolExecutionResult {
    /** Tool execution completed with bounded JSON. */
    public data class Completed(public val outputJson: String) : ToolExecutionResult

    /** Tool execution was denied by its own scoped policy. */
    public data class Rejected(public val outputJson: String) : ToolExecutionResult

    /** Tool execution failed without exposing private diagnostics. */
    public data class Failed(public val outputJson: String) : ToolExecutionResult
}

public const val MAX_TOOL_CALL_ID_CHARACTERS: Int = 128
public const val MAX_TOOL_DESCRIPTION_CHARACTERS: Int = 1_024
public const val MAX_TOOL_JSON_CHARACTERS: Int = 32 * 1_024
public const val MAX_TOOL_OUTPUT_CHARACTERS: Int = 64 * 1_024

private val TOOL_NAME_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")
