package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.RunDiagnosticReportGenerator
import io.github.artemidius.blowhard.core.api.RunDiagnosticReportResult
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspectionResult
import io.github.artemidius.blowhard.core.api.RunInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Loads content-free run inspections for a shared client.
 *
 * Lookups are passive, replace older requests, and convert unexpected failures into a generic
 * presentation state. Call [close] when the owning client shuts down.
 */
public class RunInspectorPresenter(
    private val inspector: RunInspector,
    private val reportGenerator: RunDiagnosticReportGenerator,
    private val reportSaver: RunDiagnosticReportSaver,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val events = Channel<InspectorPresenterEvent>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<RunInspectorUiState>(RunInspectorUiState.Empty)
    private var requestSequence: Long = 0
    private var activeRequest: Long = 0
    private var lookup: Job? = null
    private var export: Job? = null

    /** Current immutable inspector state. */
    public val state: StateFlow<RunInspectorUiState> = mutableState.asStateFlow()

    init {
        scope.launch { processEvents() }
    }

    /** Queues [action] for serialized handling without blocking the UI thread. */
    public fun dispatch(action: RunInspectorAction) {
        events.trySend(InspectorPresenterEvent.Action(action))
    }

    /** Cancels presentation work without changing the inspected run. */
    public fun close() {
        events.close()
        scope.cancel()
    }

    private suspend fun processEvents() {
        for (event in events) {
            when (event) {
                is InspectorPresenterEvent.Action -> handle(event.action)

                is InspectorPresenterEvent.Loaded -> if (event.request == activeRequest) {
                    mutableState.value = event.state
                }

                is InspectorPresenterEvent.Exported -> if (event.request == activeRequest) {
                    val ready = mutableState.value as? RunInspectorUiState.Ready
                    if (ready?.runId == event.runId) {
                        mutableState.value = ready.copy(exportStatus = event.status)
                    }
                }
            }
        }
    }

    private fun handle(action: RunInspectorAction) {
        when (action) {
            is RunInspectorAction.Open -> beginLookup(action.runId)
            RunInspectorAction.Refresh -> mutableState.value.runId?.let(::beginLookup)
            RunInspectorAction.Export -> beginExport()
        }
    }

    private fun beginLookup(runId: RunId) {
        lookup?.cancel()
        export?.cancel()
        requestSequence += 1
        activeRequest = requestSequence
        mutableState.value = RunInspectorUiState.Loading(runId)
        val request = activeRequest
        lookup = scope.launch {
            val loaded = load(runId)
            events.send(InspectorPresenterEvent.Loaded(request, loaded))
        }
    }

    private fun beginExport() {
        val ready = mutableState.value as? RunInspectorUiState.Ready ?: return
        if (ready.exportStatus == RunDiagnosticExportStatus.SAVING) return
        mutableState.value = ready.copy(exportStatus = RunDiagnosticExportStatus.SAVING)
        val request = activeRequest
        export = scope.launch {
            val status = export(ready.runId)
            events.send(InspectorPresenterEvent.Exported(request, ready.runId, status))
        }
    }

    private suspend fun export(runId: RunId): RunDiagnosticExportStatus = try {
        when (val generated = reportGenerator.generate(runId)) {
            is RunDiagnosticReportResult.Generated -> when (reportSaver.save(generated.report)) {
                RunDiagnosticReportSaveResult.SAVED -> RunDiagnosticExportStatus.SAVED
                RunDiagnosticReportSaveResult.CANCELLED -> RunDiagnosticExportStatus.CANCELLED
                RunDiagnosticReportSaveResult.UNAVAILABLE -> RunDiagnosticExportStatus.FAILED
            }

            RunDiagnosticReportResult.NotFound,
            RunDiagnosticReportResult.Unavailable,
            -> RunDiagnosticExportStatus.FAILED
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        RunDiagnosticExportStatus.FAILED
    }

    private suspend fun load(runId: RunId): RunInspectorUiState = try {
        when (val result = inspector.inspect(runId)) {
            is RunInspectionResult.Found -> RunInspectorUiState.Ready(result.inspection)
            RunInspectionResult.NotFound -> RunInspectorUiState.NotFound(runId)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        RunInspectorUiState.Failed(runId)
    }
}

private sealed interface InspectorPresenterEvent {
    data class Action(val action: RunInspectorAction) : InspectorPresenterEvent

    data class Loaded(val request: Long, val state: RunInspectorUiState) : InspectorPresenterEvent

    data class Exported(val request: Long, val runId: RunId, val status: RunDiagnosticExportStatus) :
        InspectorPresenterEvent
}
