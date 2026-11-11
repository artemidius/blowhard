package io.github.artemidius.blowhard.core.domain

/** Structured-output boundary for extracting one typed meeting note. */
public fun interface MeetingNoteGenerationPort {
    /** Generates one untrusted JSON result without owning validation, persistence, or retry. */
    public suspend fun generate(request: MeetingNoteGenerationRequest): MeetingNoteGenerationResult
}

/** Bounded prompt and JSON Schema supplied to a structured-output provider. */
public class MeetingNoteGenerationRequest(
    public val instructions: String,
    public val sourceJson: String,
    public val schemaName: String,
    public val schemaJson: String,
    public val maximumOutputTokens: Int,
) {
    init {
        require(instructions.isNotBlank() && instructions.length <= MAX_INSTRUCTION_CHARACTERS) {
            "Meeting-note instructions are invalid"
        }
        require(sourceJson.isNotBlank() && sourceJson.length <= MAX_SOURCE_CHARACTERS) {
            "Meeting-note source is invalid"
        }
        require(schemaName.isNotBlank() && schemaName.length <= MAX_SCHEMA_NAME_CHARACTERS) {
            "Meeting-note schema name is invalid"
        }
        require(schemaJson.isNotBlank() && schemaJson.length <= MAX_SCHEMA_CHARACTERS) {
            "Meeting-note schema is invalid"
        }
        require(maximumOutputTokens in 1..MAX_OUTPUT_TOKENS) { "Meeting-note output limit is invalid" }
    }

    /** Portable request limits. */
    public companion object {
        /** Maximum system-instruction length in Kotlin characters. */
        public const val MAX_INSTRUCTION_CHARACTERS: Int = 4_000

        /** Maximum escaped source-payload length in Kotlin characters. */
        public const val MAX_SOURCE_CHARACTERS: Int = 12 * 1024 * 1024

        /** Maximum schema identifier length in Kotlin characters. */
        public const val MAX_SCHEMA_NAME_CHARACTERS: Int = 128

        /** Maximum serialized JSON Schema length in Kotlin characters. */
        public const val MAX_SCHEMA_CHARACTERS: Int = 64 * 1024

        /** Maximum provider output budget accepted by the portable contract. */
        public const val MAX_OUTPUT_TOKENS: Int = 8_192
    }
}

/** Untrusted provider outcome before strict application validation. */
public sealed interface MeetingNoteGenerationResult {
    /** Raw JSON and the provider-reported model identity. */
    public data class Generated(public val json: String, public val modelId: String) : MeetingNoteGenerationResult {
        init {
            require(modelId.isNotBlank()) { "Provider model identity must not be blank" }
        }
    }

    /** Provider execution failed without a publishable result. */
    public data class Failed(public val kind: MeetingNoteGenerationFailureKind) : MeetingNoteGenerationResult
}

/** Content-safe structured-generation failures. */
public enum class MeetingNoteGenerationFailureKind {
    AUTHENTICATION,
    RATE_LIMITED,
    INPUT_REJECTED,
    REMOTE_FAILURE,
    INVALID_RESPONSE,
}
