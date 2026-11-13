package io.github.artemidius.blowhard.composition

import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Routes structured generation through the currently verified model and owns created adapters. */
public class VerifiedModelMeetingNoteGenerationRouter(
    private val connectionSettings: ConnectionSettings,
    private val factory: (String) -> OwnedMeetingNoteGenerationPort,
) : MeetingNoteGenerationPort {
    private val access: Mutex = Mutex()
    private val ports: MutableMap<String, OwnedMeetingNoteGenerationPort> = mutableMapOf()
    private var closed: Boolean = false

    override suspend fun generate(request: MeetingNoteGenerationRequest): MeetingNoteGenerationResult =
        connectionSettings.state.value
            .takeIf { it.canUseLiveMode }
            ?.selectedModelId
            ?.let { generateWith(it, request) }
            ?: failed(MeetingNoteGenerationFailureKind.AUTHENTICATION)

    /** Closes every model-specific adapter exactly once and rejects future work. */
    public suspend fun close() {
        val owned = access.withLock {
            if (closed) return
            closed = true
            ports.values.toList().also { ports.clear() }
        }
        owned.forEach(OwnedMeetingNoteGenerationPort::close)
    }

    private suspend fun portFor(modelId: String): OwnedMeetingNoteGenerationPort? = access.withLock {
        if (closed) return@withLock null
        ports[modelId] ?: createSafely(modelId)?.also { ports[modelId] = it }
    }

    private suspend fun generateWith(
        modelId: String,
        request: MeetingNoteGenerationRequest,
    ): MeetingNoteGenerationResult = portFor(modelId)?.generation?.generate(request)
        ?: failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE)

    private fun createSafely(modelId: String): OwnedMeetingNoteGenerationPort? = try {
        factory(modelId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/** One provider adapter and the resource cleanup paired with it. */
public class OwnedMeetingNoteGenerationPort(
    public val generation: MeetingNoteGenerationPort,
    private val closeAction: () -> Unit,
) {
    /** Releases the adapter's resources. The owning router invokes this once. */
    public fun close(): Unit = closeAction()
}

private fun failed(kind: MeetingNoteGenerationFailureKind): MeetingNoteGenerationResult.Failed =
    MeetingNoteGenerationResult.Failed(kind)
