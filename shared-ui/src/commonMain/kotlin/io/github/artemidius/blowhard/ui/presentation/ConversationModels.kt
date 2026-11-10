package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationTarget

/** Immutable state rendered by the shared conversation screen. */
public data class ConversationUiState(
    public val conversations: List<ConversationUiModel>,
    public val selectedConversationId: ConversationId,
    public val composer: String = "",
    public val scenario: DemoScenario = DemoScenario.SUCCESS,
) {
    init {
        require(conversations.any { it.id == selectedConversationId }) {
            "Selected conversation must exist"
        }
    }

    /** Conversation currently visible in the message pane. */
    public val selectedConversation: ConversationUiModel
        get() = conversations.first { it.id == selectedConversationId }

    /** Whether the current composer contents may start a new run. */
    public val canSend: Boolean
        get() = composer.isNotBlank() && !selectedConversation.runState.isActive

    /** Whether the selected conversation owns a cancellable run. */
    public val canStop: Boolean
        get() = selectedConversation.runState.isActive

    /** Feedback owned by the currently visible conversation. */
    public val notice: UiNotice?
        get() = selectedConversation.notice
}

/** One conversation listed in the shared workspace. */
public data class ConversationUiModel(
    public val id: ConversationId,
    public val title: String,
    public val messages: List<ChatMessageUi> = emptyList(),
    public val runState: ConversationRunState = ConversationRunState.IDLE,
    public val notice: UiNotice? = null,
    public val latestTarget: GenerationTarget? = null,
)

/** One safely rendered message in a conversation. */
public data class ChatMessageUi(
    public val id: String,
    public val role: ChatRole,
    public val text: String,
    public val delivery: MessageDelivery = MessageDelivery.COMPLETE,
    public val target: GenerationTarget? = null,
)

/** Author represented by a message bubble. */
public enum class ChatRole {
    /** Text submitted by the local user. */
    USER,

    /** Text emitted by the configured generation provider. */
    ASSISTANT,
}

/** Presentation state of an individual message. */
public enum class MessageDelivery {
    /** The message is immutable and complete. */
    COMPLETE,

    /** The assistant message may receive additional text. */
    STREAMING,

    /** Generation ended with a classified failure. */
    FAILED,

    /** Generation was cancelled by the user or application lifecycle. */
    CANCELLED,
}

/** Current lifecycle state presented for a conversation. */
public enum class ConversationRunState {
    /** No attempt is active. */
    IDLE,

    /** A start request is being validated by the application layer. */
    STARTING,

    /** The accepted attempt is waiting for an execution permit. */
    QUEUED,

    /** Provider output is being streamed. */
    STREAMING,

    /** The latest attempt completed successfully. */
    SUCCEEDED,

    /** The latest attempt ended with a classified failure. */
    FAILED,

    /** The latest attempt was cancelled. */
    CANCELLED,
    ;

    /** Whether this state prevents another run in the same conversation. */
    public val isActive: Boolean
        get() = this == STARTING || this == QUEUED || this == STREAMING
}

/** Non-sensitive feedback presented above the composer. */
public data class UiNotice(public val message: String)

/** Bundled offline behavior selected for the next demonstration run. */
public enum class DemoScenario(public val label: String, public val description: String) {
    /** Streams a complete Markdown-shaped response. */
    SUCCESS("Success", "Streams a complete response"),

    /** Emits partial output followed by a classified provider failure. */
    FAILURE("Failure", "Ends with a provider failure"),

    /** Demonstrates a safe rate-limit error. */
    RATE_LIMITED("Rate limited", "Returns a rate-limit response"),

    /** Abruptly interrupts the synthetic provider stream. */
    INTERRUPTED("Interrupted", "Stops the stream unexpectedly"),
}
