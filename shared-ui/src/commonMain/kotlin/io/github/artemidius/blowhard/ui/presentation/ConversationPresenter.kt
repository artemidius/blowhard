package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ConversationLibrary
import io.github.artemidius.blowhard.core.api.ConversationRuns
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunAttachment
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Converts user actions and the shared run API into immutable conversation screen state.
 *
 * One serialized event loop owns presentation changes. Observing a run is passive and happens
 * exactly once for every start accepted through this presenter. Call [close] at client shutdown.
 */
public class ConversationPresenter(
    private val runs: ConversationRuns,
    private val library: ConversationLibrary,
    private val scenarioSelection: DemoScenarioSelection,
    clipboardWriter: ClipboardWriter,
    coroutineContext: CoroutineContext,
    initialState: ConversationUiState = initialConversationState(),
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val events = Channel<PresenterEvent>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialState)
    private val activeRuns = mutableMapOf<ConversationId, ActiveRun>()
    private val observers = mutableMapOf<ConversationId, Job>()
    private val messageClipboard = MessageClipboard(clipboardWriter)
    private val conversationIds = PresentationIdSequence(
        prefix = "conversation",
        reserved = initialState.conversations.mapTo(mutableSetOf()) { it.id.value },
    )
    private val conversationManagement = ConversationManagementPresenter(library, conversationIds)
    private val messageIds = PresentationIdSequence(
        prefix = "message",
        reserved = initialState.conversations.flatMapTo(mutableSetOf()) { conversation ->
            conversation.messages.map { it.id }
        },
    )

    /** Current immutable state of the conversation workspace. */
    public val state: StateFlow<ConversationUiState> = mutableState.asStateFlow()

    init {
        scope.launch { processEvents() }
    }

    /** Queues [action] for serialized handling without blocking the UI thread. */
    public fun dispatch(action: ConversationAction) {
        events.trySend(PresenterEvent.Action(action))
    }

    /** Stops presentation work and passive run observers. The application still owns run closure. */
    public fun close() {
        events.close()
        scope.cancel()
    }

    private suspend fun processEvents() {
        for (event in events) {
            when (event) {
                is PresenterEvent.Action -> handle(event.action)
                is PresenterEvent.Snapshot -> handleSnapshot(event.snapshot)
            }
        }
    }

    private suspend fun handle(action: ConversationAction) {
        if (action.isConversationManagement()) {
            mutableState.value = conversationManagement.handle(mutableState.value, action)
            return
        }
        action.applyLocalState(mutableState.value)?.let {
            mutableState.value = it
            return
        }
        when (action) {
            ConversationAction.Send -> mutableState.value.sendAttempt()?.let { startAttempt(it) }

            ConversationAction.Stop -> stop()

            is ConversationAction.Regenerate ->
                mutableState.value.regenerationAttempt(action.messageId)?.let { startAttempt(it) }

            is ConversationAction.CopyMessage -> {
                mutableState.value = messageClipboard.copy(mutableState.value, action.messageId)
            }

            is ConversationAction.ComposerChanged,
            is ConversationAction.ConversationSelected,
            is ConversationAction.ScenarioChanged,
            ConversationAction.NewConversation,
            ConversationAction.DeleteConversationConfirmed,
            ConversationAction.DeleteConversationDismissed,
            is ConversationAction.DeleteConversationRequested,
            ConversationAction.ImageCleared,
            is ConversationAction.ImageSelected,
            ConversationAction.NoticeDismissed,
            -> error("Conversation management action was not routed")
        }
    }

    private suspend fun startAttempt(attempt: StartAttempt) {
        val conversationId = attempt.beforeStart.selectedConversationId
        mutableState.value = attempt.beforeStart.prepareRunStart(conversationId, attempt.clearComposer)
        val result = requestStart(attempt, conversationId)
        if (result == null) {
            mutableState.value = attempt.beforeStart.withSelectedNotice(UiNotice(START_FAILURE_MESSAGE))
        } else {
            when (result) {
                is StartRunResult.Started -> observe(
                    result.runId,
                    conversationId,
                    attempt.prompt,
                    result.target,
                    attempt.image,
                )

                is StartRunResult.Rejected -> {
                    mutableState.value =
                        attempt.beforeStart.withSelectedNotice(UiNotice(result.reason.safeMessage()))
                }
            }
        }
    }

    private suspend fun requestStart(attempt: StartAttempt, conversationId: ConversationId): StartRunResult? = try {
        scenarioSelection.prepare(conversationId, attempt.beforeStart.scenario)
        val attachment = attempt.image?.let { RunAttachment(it.documentId) }
        runs.start(StartRunRequest(conversationId, attempt.prompt, attachment))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private suspend fun observe(
        runId: RunId,
        conversationId: ConversationId,
        prompt: String,
        target: GenerationTarget,
        image: SelectedImageUi?,
    ) {
        val userMessageId = messageIds.next()
        val assistantMessageId = messageIds.next()
        activeRuns[conversationId] = ActiveRun(runId, assistantMessageId)
        mutableState.value = mutableState.value.beginRun(
            conversationId = conversationId,
            runId = runId,
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            target = target,
            attachedImageName = image?.displayName,
        )
        val observation = try {
            runs.observe(runId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        when (observation) {
            is ObserveRunResult.Found -> observers[conversationId] = scope.launch {
                observation.snapshots.collect { events.send(PresenterEvent.Snapshot(it)) }
            }

            ObserveRunResult.NotFound, null -> failRun(conversationId, OBSERVE_FAILURE_MESSAGE)
        }
    }

    private suspend fun stop() {
        val conversationId = mutableState.value.selectedConversationId
        val active = activeRuns[conversationId] ?: return
        val result = try {
            runs.cancel(active.runId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failRun(conversationId, CANCEL_FAILURE_MESSAGE)
            return
        }
        if (result == CancelRunResult.NOT_FOUND) failRun(conversationId, CANCEL_FAILURE_MESSAGE)
    }

    private fun handleSnapshot(snapshot: RunSnapshot) {
        val active = activeRuns[snapshot.conversationId] ?: return
        if (active.runId != snapshot.runId) return
        val presented = snapshot.present()
        mutableState.value = mutableState.value.applySnapshot(
            conversationId = snapshot.conversationId,
            assistantMessageId = active.assistantMessageId,
            snapshot = presented,
        )
        if (presented.terminal) {
            activeRuns.remove(snapshot.conversationId)
            observers.remove(snapshot.conversationId)?.cancel()
        }
    }

    private fun failRun(conversationId: ConversationId, message: String) {
        val active = activeRuns.remove(conversationId)
        observers.remove(conversationId)?.cancel()
        mutableState.value = mutableState.value.updateConversation(conversationId) {
            it.copy(
                runState = ConversationRunState.FAILED,
                notice = UiNotice(message),
                messages = it.messages.map { chatMessage ->
                    if (chatMessage.id == active?.assistantMessageId) {
                        chatMessage.copy(delivery = MessageDelivery.FAILED)
                    } else {
                        chatMessage
                    }
                },
            )
        }
    }
}

private data class ActiveRun(val runId: RunId, val assistantMessageId: String)

private data class StartAttempt(
    val beforeStart: ConversationUiState,
    val prompt: String,
    val clearComposer: Boolean,
    val image: SelectedImageUi?,
)

private fun ConversationUiState.sendAttempt(): StartAttempt? = if (canSend) {
    StartAttempt(this, composer.trim(), clearComposer = true, image = selectedImage)
} else {
    null
}

private fun ConversationUiState.regenerationAttempt(messageId: String): StartAttempt? =
    regenerationPrompt(messageId)?.let { prompt ->
        StartAttempt(this, prompt, clearComposer = false, image = null)
    }

private sealed interface PresenterEvent {
    data class Action(val action: ConversationAction) : PresenterEvent

    data class Snapshot(val snapshot: RunSnapshot) : PresenterEvent
}

private fun StartRunRejection.safeMessage(): String = when (this) {
    StartRunRejection.BLANK_PROMPT -> "Enter a message before starting generation."

    StartRunRejection.CONVERSATION_BUSY -> "This conversation already has an active run."

    StartRunRejection.LIVE_CONFIGURATION_NOT_READY ->
        "Live configuration is not ready. Reverify the credential and selected model."

    StartRunRejection.MODEL_DOES_NOT_SUPPORT_VISION ->
        "The selected model does not advertise vision support. Choose a vision model or remove the image."

    StartRunRejection.ATTACHMENT_NOT_AVAILABLE ->
        "The selected image is no longer available. Choose it again from Local documents."

    StartRunRejection.ATTACHMENT_NOT_SUPPORTED ->
        "The selected attachment is not a supported bounded image."

    StartRunRejection.STORAGE_UNAVAILABLE ->
        "Local conversation history is unavailable. Generation was not started."

    StartRunRejection.LIFECYCLE_CLOSED -> "The application is shutting down and cannot start another run."
}

private const val START_FAILURE_MESSAGE = "The run could not be started safely."
private const val OBSERVE_FAILURE_MESSAGE = "The accepted run could not be observed."
private const val CANCEL_FAILURE_MESSAGE = "The active run could not be stopped safely."

private fun ConversationAction.isConversationManagement(): Boolean = when (this) {
    ConversationAction.NewConversation,
    ConversationAction.DeleteConversationConfirmed,
    ConversationAction.DeleteConversationDismissed,
    is ConversationAction.DeleteConversationRequested,
    -> true

    else -> false
}

private fun ConversationAction.applyLocalState(state: ConversationUiState): ConversationUiState? = when (this) {
    is ConversationAction.ComposerChanged -> state.copy(composer = value)

    is ConversationAction.ConversationSelected -> if (state.conversations.any { it.id == id }) {
        state.copy(selectedConversationId = id)
    } else {
        state
    }

    is ConversationAction.ScenarioChanged -> state.copy(scenario = scenario)

    is ConversationAction.ImageSelected -> displayName.takeIf {
        it.isNotBlank() && it.length <= ImportedDocument.MAX_DISPLAY_NAME_LENGTH
    }?.let {
        state.copy(selectedImage = SelectedImageUi(id, it))
    } ?: state

    ConversationAction.ImageCleared -> state.copy(selectedImage = null)

    ConversationAction.NoticeDismissed -> state.withSelectedNotice(null)

    else -> null
}
