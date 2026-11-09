package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ConversationRuns
import io.github.artemidius.blowhard.core.api.ObserveRunResult
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
    private val scenarioSelection: DemoScenarioSelection,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val events = Channel<PresenterEvent>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialConversationState())
    private val activeRuns = mutableMapOf<ConversationId, ActiveRun>()
    private val observers = mutableMapOf<ConversationId, Job>()
    private val conversationIds = PresentationIdSequence("conversation", initialValue = 1L)
    private val messageIds = PresentationIdSequence("message")

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
        when (action) {
            is ConversationAction.ComposerChanged ->
                mutableState.value =
                    mutableState.value.copy(composer = action.value)

            is ConversationAction.ConversationSelected -> if (
                mutableState.value.conversations.any { it.id == action.id }
            ) {
                mutableState.value = mutableState.value.copy(selectedConversationId = action.id)
            }

            is ConversationAction.ScenarioChanged ->
                mutableState.value =
                    mutableState.value.copy(scenario = action.scenario)

            ConversationAction.NewConversation -> createConversation()

            ConversationAction.Send -> send()

            ConversationAction.Stop -> stop()

            ConversationAction.NoticeDismissed -> mutableState.value = mutableState.value.withSelectedNotice(null)
        }
    }

    private suspend fun send() {
        val beforeStart = mutableState.value
        if (!beforeStart.canSend) return
        val conversationId = beforeStart.selectedConversationId
        val prompt = beforeStart.composer.trim()
        mutableState.value = beforeStart.updateConversation(conversationId) {
            it.copy(runState = ConversationRunState.STARTING, notice = null)
        }.copy(composer = "")
        val scenarioPrepared = try {
            scenarioSelection.prepare(conversationId, beforeStart.scenario)
            true
        } catch (_: Throwable) {
            false
        }

        val result = if (scenarioPrepared) {
            try {
                runs.start(StartRunRequest(conversationId, prompt))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        if (result == null) {
            mutableState.value = beforeStart.withSelectedNotice(UiNotice(START_FAILURE_MESSAGE))
        } else {
            when (result) {
                is StartRunResult.Started -> observe(result.runId, conversationId, prompt)

                is StartRunResult.Rejected -> {
                    mutableState.value = beforeStart.withSelectedNotice(UiNotice(result.reason.safeMessage()))
                }
            }
        }
    }

    private suspend fun observe(runId: RunId, conversationId: ConversationId, prompt: String) {
        val userMessageId = messageIds.next()
        val assistantMessageId = messageIds.next()
        activeRuns[conversationId] = ActiveRun(runId, assistantMessageId)
        mutableState.value = mutableState.value.beginRun(
            conversationId = conversationId,
            prompt = prompt,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
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

    private fun createConversation() {
        val id = ConversationId(conversationIds.next())
        mutableState.value = mutableState.value.copy(
            conversations = mutableState.value.conversations + ConversationUiModel(id, "New conversation"),
            selectedConversationId = id,
            composer = "",
        )
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

private sealed interface PresenterEvent {
    data class Action(val action: ConversationAction) : PresenterEvent

    data class Snapshot(val snapshot: RunSnapshot) : PresenterEvent
}

private fun StartRunRejection.safeMessage(): String = when (this) {
    StartRunRejection.BLANK_PROMPT -> "Enter a message before starting generation."
    StartRunRejection.CONVERSATION_BUSY -> "This conversation already has an active run."
    StartRunRejection.LIFECYCLE_CLOSED -> "The application is shutting down and cannot start another run."
}

private const val START_FAILURE_MESSAGE = "The run could not be started safely."
private const val OBSERVE_FAILURE_MESSAGE = "The accepted run could not be observed."
private const val CANCEL_FAILURE_MESSAGE = "The active run could not be stopped safely."
