package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunId

/** Author represented by a durable local message. */
public enum class StoredMessageRole {
    /** Text submitted by the local user. */
    USER,

    /** Text emitted by a configured generation source. */
    ASSISTANT,
}

/** One ordered message persisted independently from generation-attempt metadata. */
public data class StoredMessage(
    public val id: MessageId,
    public val sequence: Long,
    public val role: StoredMessageRole,
    public val text: String,
    public val createdAtEpochMilliseconds: Long,
) {
    init {
        require(sequence >= 0) { "Message sequence must not be negative" }
        require(createdAtEpochMilliseconds >= 0) { "Message creation time must not be negative" }
        require(role != StoredMessageRole.USER || text.isNotBlank()) { "User message must not be blank" }
    }
}

/** Durable lifecycle state of one generation attempt. */
public enum class StoredAttemptStatus {
    /** Accepted but not yet executing. */
    QUEUED,

    /** Provider execution was active at the latest checkpoint. */
    RUNNING,

    /** Generation completed successfully. */
    SUCCEEDED,

    /** Generation ended with a safely classified failure. */
    FAILED,

    /** Generation was cancelled explicitly. */
    CANCELLED,

    /** Nonterminal work was recovered after process interruption. */
    INTERRUPTED,
}

/** Immutable routing and outcome snapshot for one persisted generation attempt. */
public data class StoredGenerationAttempt(
    public val runId: RunId,
    public val userMessageId: MessageId,
    public val assistantMessageId: MessageId,
    public val target: GenerationTarget,
    public val parameters: GenerationParameters,
    public val status: StoredAttemptStatus,
    public val failure: RunFailure? = null,
    public val createdAtEpochMilliseconds: Long,
    public val updatedAtEpochMilliseconds: Long,
) {
    init {
        require(createdAtEpochMilliseconds >= 0) { "Attempt creation time must not be negative" }
        require(updatedAtEpochMilliseconds >= createdAtEpochMilliseconds) {
            "Attempt update time must not precede creation time"
        }
        require((status == StoredAttemptStatus.FAILED) == (failure != null)) {
            "Only failed attempts carry failure details"
        }
    }
}

/** Complete durable message and generation-attempt state for one conversation. */
public data class StoredTranscript(
    public val conversationId: ConversationId,
    public val messages: List<StoredMessage>,
    public val attempts: List<StoredGenerationAttempt>,
) {
    init {
        requireUnique(messages.map { it.id }, "message identity")
        requireUnique(messages.map { it.sequence }, "message sequence")
        requireUnique(attempts.map { it.runId }, "attempt identity")
        requireUnique(attempts.map { it.userMessageId }, "attempt user message")
        requireUnique(attempts.map { it.assistantMessageId }, "attempt assistant message")

        val messagesById = messages.associateBy { it.id }
        attempts.forEach { attempt ->
            require(messagesById[attempt.userMessageId]?.role == StoredMessageRole.USER) {
                "Attempt must reference a stored user message"
            }
            require(messagesById[attempt.assistantMessageId]?.role == StoredMessageRole.ASSISTANT) {
                "Attempt must reference a stored assistant message"
            }
        }
    }
}

private fun <T> requireUnique(values: List<T>, label: String) {
    require(values.toSet().size == values.size) { "Duplicate $label" }
}
