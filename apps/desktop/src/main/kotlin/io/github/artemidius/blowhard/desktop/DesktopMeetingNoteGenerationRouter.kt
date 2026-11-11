package io.github.artemidius.blowhard.desktop

import io.github.artemidius.blowhard.adapters.mistral.MistralChatConfiguration
import io.github.artemidius.blowhard.adapters.mistral.MistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mistral.MistralMeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationFailureKind
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationPort
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationRequest
import io.github.artemidius.blowhard.core.domain.MeetingNoteGenerationResult
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Routes structured generation to the currently verified model without exposing credentials. */
internal class DesktopMeetingNoteGenerationRouter(
    private val connectionSettings: ConnectionSettings,
    private val factory: (String) -> OwnedMeetingNoteGenerationPort,
) : MeetingNoteGenerationPort,
    AutoCloseable {
    private val ports = ConcurrentHashMap<String, OwnedMeetingNoteGenerationPort>()
    private val closed = AtomicBoolean(false)

    override suspend fun generate(request: MeetingNoteGenerationRequest): MeetingNoteGenerationResult = when {
        closed.get() -> failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE)
        else -> generateWithVerifiedModel(request)
    }

    private suspend fun generateWithVerifiedModel(request: MeetingNoteGenerationRequest): MeetingNoteGenerationResult {
        val settings = connectionSettings.state.value
        val modelId = settings.selectedModelId?.takeIf { settings.canUseLiveMode }
        return modelId?.let { generateWithPort(it, request) }
            ?: failed(MeetingNoteGenerationFailureKind.AUTHENTICATION)
    }

    private suspend fun generateWithPort(
        modelId: String,
        request: MeetingNoteGenerationRequest,
    ): MeetingNoteGenerationResult {
        val port = try {
            ports.computeIfAbsent(modelId, factory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return port?.generation?.generate(request) ?: failed(MeetingNoteGenerationFailureKind.REMOTE_FAILURE)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ports.values.forEach(OwnedMeetingNoteGenerationPort::close)
        ports.clear()
    }

    companion object {
        fun mistral(
            connectionSettings: ConnectionSettings,
            credentials: MistralCredentialProvider,
        ): DesktopMeetingNoteGenerationRouter = DesktopMeetingNoteGenerationRouter(connectionSettings) { modelId ->
            val port = MistralMeetingNoteGenerationPort.create(credentials, MistralChatConfiguration(modelId))
            OwnedMeetingNoteGenerationPort(port, port::close)
        }
    }
}

internal class OwnedMeetingNoteGenerationPort(
    val generation: MeetingNoteGenerationPort,
    private val closeAction: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) closeAction()
    }
}

private fun failed(kind: MeetingNoteGenerationFailureKind): MeetingNoteGenerationResult.Failed =
    MeetingNoteGenerationResult.Failed(kind)
