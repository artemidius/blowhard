package io.github.artemidius.blowhard.ui.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Requests destructive local-data erasure through an application-host boundary. */
public fun interface LocalDataEraser {
    /** Erases host-owned local data after active application resources have closed. */
    public suspend fun erase(): LocalDataResetResult
}

/** Sanitized result of a host-owned local-data erasure attempt. */
public enum class LocalDataResetResult {
    /** Every host-owned local-data target was erased or was already absent. */
    ERASED,

    /** At least one host-owned local-data target could not be erased. */
    FAILED,
}

/** Immutable presentation state for the destructive local-data action. */
public data class LocalDataResetUiState(
    public val confirmationRequested: Boolean = false,
    public val inProgress: Boolean = false,
    public val outcome: LocalDataResetResult? = null,
)

/**
 * Serializes explicit local-data reset confirmation and one host erasure request.
 *
 * The presenter is intentionally independent from the application runtime that the eraser may
 * close and replace. Call [close] when the platform host itself shuts down.
 */
public class LocalDataResetPresenter(private val eraser: LocalDataEraser, coroutineContext: CoroutineContext) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val events = Channel<LocalDataResetEvent>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(LocalDataResetUiState())

    /** Current confirmation, progress, and sanitized outcome state. */
    public val state: StateFlow<LocalDataResetUiState> = mutableState.asStateFlow()

    init {
        scope.launch { processEvents() }
    }

    internal fun dispatch(action: LocalDataResetAction) {
        events.trySend(LocalDataResetEvent.Action(action))
    }

    /** Cancels presentation work without initiating or repeating erasure. */
    public fun close() {
        events.close()
        scope.cancel()
    }

    private suspend fun processEvents() {
        for (event in events) {
            when (event) {
                is LocalDataResetEvent.Action -> handle(event.action)
                is LocalDataResetEvent.Completed -> complete(event.result)
            }
        }
    }

    private fun handle(action: LocalDataResetAction) {
        when (action) {
            LocalDataResetAction.Requested -> requestConfirmation()
            LocalDataResetAction.Cancelled -> cancelConfirmation()
            LocalDataResetAction.Confirmed -> beginErasure()
            LocalDataResetAction.OutcomeDismissed -> dismissOutcome()
        }
    }

    private fun requestConfirmation() {
        if (mutableState.value.inProgress) return
        mutableState.value = LocalDataResetUiState(confirmationRequested = true)
    }

    private fun cancelConfirmation() {
        if (!mutableState.value.confirmationRequested || mutableState.value.inProgress) return
        mutableState.value = LocalDataResetUiState()
    }

    private fun beginErasure() {
        if (!mutableState.value.confirmationRequested || mutableState.value.inProgress) return
        mutableState.value = LocalDataResetUiState(inProgress = true)
        scope.launch {
            events.send(LocalDataResetEvent.Completed(eraseSafely()))
        }
    }

    private suspend fun eraseSafely(): LocalDataResetResult = try {
        eraser.erase()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        LocalDataResetResult.FAILED
    }

    private fun complete(result: LocalDataResetResult) {
        mutableState.value = LocalDataResetUiState(outcome = result)
    }

    private fun dismissOutcome() {
        if (mutableState.value.inProgress) return
        mutableState.value = mutableState.value.copy(outcome = null)
    }
}

internal enum class LocalDataResetAction {
    Requested,
    Cancelled,
    Confirmed,
    OutcomeDismissed,
}

private sealed interface LocalDataResetEvent {
    data class Action(val action: LocalDataResetAction) : LocalDataResetEvent

    data class Completed(val result: LocalDataResetResult) : LocalDataResetEvent
}
