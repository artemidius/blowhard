package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.domain.GenerationImage
import io.github.artemidius.blowhard.core.domain.PromptText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

internal class RunRecord(
    val runId: RunId,
    val conversationId: ConversationId,
    val prompt: PromptText,
    val target: GenerationTarget,
    val parameters: GenerationParameters,
    val image: GenerationImage?,
    initialSnapshot: RunSnapshot,
) {
    val snapshots = MutableStateFlow(initialSnapshot)
    val inspection = MutableStateFlow(
        RunInspection(
            runId = runId,
            conversationId = conversationId,
            target = target,
            parameters = parameters,
            events = listOf(RunInspectionEvent.Accepted(initialSnapshot.updatedAt)),
        ),
    )
    var job: Job? = null
    var cancellationRequested: Boolean = false
    var durableCheckpoint: DurableRunCheckpoint? = null
}

internal fun RunSnapshot.isTerminal(): Boolean =
    this is RunSnapshot.Succeeded || this is RunSnapshot.Failed || this is RunSnapshot.Cancelled

internal fun RunSnapshot.output(): String = when (this) {
    is RunSnapshot.Queued -> ""
    is RunSnapshot.Running -> output
    is RunSnapshot.Succeeded -> output
    is RunSnapshot.Failed -> partialOutput
    is RunSnapshot.Cancelled -> partialOutput
}
