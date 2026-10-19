package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConversationDeletionResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ConversationLibrary
import io.github.artemidius.blowhard.core.api.ConversationRuns
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspectionResult
import io.github.artemidius.blowhard.core.api.RunInspector
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationRequest
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Application-owned implementation of the conversation run lifecycle.
 *
 * Each accepted start creates one provider collection. Observation is passive, concurrent work is
 * bounded by [policy], and terminal snapshots are immutable. Call [close] when the owning
 * application shuts down.
 */
public class DefaultConversationRuns(
    private val generation: TextGenerationPort,
    private val idFactory: RunIdFactory,
    private val clock: RunClock,
    coroutineContext: CoroutineContext,
    policy: RunExecutionPolicy = RunExecutionPolicy(),
    private val connectionSettings: ConnectionSettings? = null,
    conversationStore: ConversationStore? = null,
    checkpointPolicy: RunCheckpointPolicy = RunCheckpointPolicy(),
    documentProcessingStore: DocumentProcessingStore? = null,
) : ConversationRuns,
    ConversationLibrary,
    RunInspector {
    private val validator = StartRunRequestValidator()
    private val repository = InMemoryRunRepository(clock)
    private val permits = Semaphore(policy.maxConcurrentRuns)
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val durableRuns = DurableRunCoordinator(
        conversationStore?.let { ConversationRunCheckpoints(it, checkpointPolicy) },
        repository,
        clock,
    )
    private val deletion = ConversationDeletionCoordinator(conversationStore, repository)
    private val imageResolver = RunImageResolver(documentProcessingStore)

    override suspend fun start(request: StartRunRequest): StartRunResult = when (
        val validation = validator.validate(request)
    ) {
        is StartRunValidation.Accepted -> startValidated(request, validation)
        is StartRunValidation.Rejected -> StartRunResult.Rejected(validation.reason)
    }

    private suspend fun startValidated(
        request: StartRunRequest,
        validation: StartRunValidation.Accepted,
    ): StartRunResult {
        val runConfiguration = resolveRunConfiguration()
        return if (runConfiguration == null) {
            StartRunResult.Rejected(StartRunRejection.LIVE_CONFIGURATION_NOT_READY)
        } else {
            val image = when (
                val resolution = imageResolver.resolve(
                    request.attachment,
                    runConfiguration.supportsVision,
                )
            ) {
                is ImageResolution.Accepted -> resolution.image
                is ImageResolution.Rejected -> return StartRunResult.Rejected(resolution.reason)
            }
            val runId = idFactory.next()
            val record = RunRecord(
                runId = runId,
                conversationId = request.conversationId,
                prompt = validation.prompt,
                target = runConfiguration.target,
                parameters = runConfiguration.parameters,
                image = image,
                initialSnapshot = RunSnapshot.Queued(runId, request.conversationId, clock.now()),
            )
            when (repository.add(record)) {
                AddRunResult.ConversationBusy ->
                    StartRunResult.Rejected(StartRunRejection.CONVERSATION_BUSY)

                AddRunResult.Closed -> StartRunResult.Rejected(StartRunRejection.LIFECYCLE_CLOSED)

                AddRunResult.Added -> if (durableRuns.begin(record)) {
                    record.job = scope.launch(start = CoroutineStart.LAZY) { execute(record) }
                    checkNotNull(record.job).start()
                    StartRunResult.Started(runId, runConfiguration.target)
                } else {
                    check(repository.discard(record)) { "Unstarted run could not be discarded" }
                    StartRunResult.Rejected(StartRunRejection.STORAGE_UNAVAILABLE)
                }
            }
        }
    }

    override suspend fun observe(runId: RunId): ObserveRunResult {
        val record = repository.find(runId) ?: return ObserveRunResult.NotFound
        return ObserveRunResult.Found(record.snapshots)
    }

    override suspend fun cancel(runId: RunId): CancelRunResult = when (
        val decision =
            repository.requestCancellation(runId)
    ) {
        is CancellationDecision.Accepted -> {
            decision.job.cancel()
            decision.job.join()
            durableRuns.finish(decision.record, RunTerminal.Cancelled)
            CancelRunResult.ACCEPTED
        }

        is CancellationDecision.AlreadyRequested -> {
            decision.job?.join()
            durableRuns.finish(decision.record, RunTerminal.Cancelled)
            CancelRunResult.ALREADY_FINISHED
        }

        is CancellationDecision.AlreadyFinished -> CancelRunResult.ALREADY_FINISHED

        CancellationDecision.NotFound -> CancelRunResult.NOT_FOUND
    }

    override suspend fun delete(conversationId: ConversationId): ConversationDeletionResult =
        deletion.delete(conversationId)

    override suspend fun inspect(runId: RunId): RunInspectionResult = repository.find(runId)?.let {
        RunInspectionResult.Found(it.inspection.value)
    } ?: RunInspectionResult.NotFound

    /** Cancels owned work, records terminal cancellation, and rejects future starts. */
    public suspend fun close() {
        val activeRecords = repository.close()
        val jobs = activeRecords.mapNotNull(RunRecord::job)
        jobs.forEach(Job::cancel)
        jobs.joinAll()
        activeRecords.forEach { durableRuns.finish(it, RunTerminal.Cancelled) }
        scope.cancel()
    }

    private suspend fun execute(record: RunRecord) {
        try {
            permits.withPermit {
                if (!repository.update(record, ActiveRunUpdate.Started)) return@withPermit
                durableRuns.checkpoint(record, record.snapshots.value)
                collectGeneration(record)
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { durableRuns.finish(record, RunTerminal.Cancelled) }
            throw cancellation
        } catch (_: ConversationCheckpointException) {
            durableRuns.fail(record)
        } catch (_: Throwable) {
            durableRuns.finish(record, RunTerminal.Unexpected)
        }
    }

    private suspend fun collectGeneration(record: RunRecord) {
        val request = GenerationRequest(
            record.runId,
            record.conversationId,
            record.prompt,
            record.target,
            record.parameters,
            record.image,
        )
        generation.generate(request).takeWhile { event ->
            when (event) {
                is GenerationEvent.TextDelta -> repository.update(record, ActiveRunUpdate.TextDelta(event.text))
                    .also { updated -> if (updated) durableRuns.checkpoint(record, record.snapshots.value) }

                is GenerationEvent.Usage -> repository.update(record, ActiveRunUpdate.Usage(event.value))

                GenerationEvent.Completed -> {
                    durableRuns.finish(record, RunTerminal.Succeeded)
                    false
                }

                is GenerationEvent.Failed -> {
                    durableRuns.finish(record, RunTerminal.Failed(event.failure.toRunFailure()))
                    false
                }
            }
        }.collect()

        val snapshot = record.snapshots.value
        if (!snapshot.isTerminal()) {
            durableRuns.finish(record, RunTerminal.Unexpected)
        }
    }

    private fun resolveRunConfiguration(): RunConfiguration? {
        val settings = connectionSettings?.state?.value
            ?: return RunConfiguration(GenerationTarget.Demo, GenerationParameters(), supportsVision = false)
        val target = when (settings.mode) {
            GenerationMode.DEMO -> GenerationTarget.Demo

            GenerationMode.LIVE ->
                settings.selectedModelId
                    ?.takeIf { settings.canUseLiveMode }
                    ?.let(GenerationTarget::Live)
        }
        val supportsVision = settings.selectedModelId?.let { selected ->
            settings.models.firstOrNull { it.id == selected }?.supportsVision
        } ?: false
        return target?.let { RunConfiguration(it, settings.generationParameters, supportsVision) }
    }
}

private data class RunConfiguration(
    val target: GenerationTarget,
    val parameters: GenerationParameters,
    val supportsVision: Boolean,
)
