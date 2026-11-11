package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConnectionSettings
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.ConnectionStatus
import io.github.artemidius.blowhard.core.api.ConnectionTestFailureKind
import io.github.artemidius.blowhard.core.api.ConnectionTestResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.CredentialStatus
import io.github.artemidius.blowhard.core.api.CredentialUpdateResult
import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentExtraction
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.MessageId
import io.github.artemidius.blowhard.core.api.ModeSelectionResult
import io.github.artemidius.blowhard.core.api.ModelSelectionResult
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunAttachment
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.SelectableModel
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import io.github.artemidius.blowhard.core.domain.ConversationRecoveryResult
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.DocumentExtractionSaveResult
import io.github.artemidius.blowhard.core.domain.DocumentExtractionStoreLookupResult
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore
import io.github.artemidius.blowhard.core.domain.DocumentSourceLookupResult
import io.github.artemidius.blowhard.core.domain.GenerationEvent
import io.github.artemidius.blowhard.core.domain.GenerationImageMediaType
import io.github.artemidius.blowhard.core.domain.StoredAttemptStatus
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredDocumentSource
import io.github.artemidius.blowhard.core.domain.StoredGenerationAttempt
import io.github.artemidius.blowhard.core.domain.StoredMessage
import io.github.artemidius.blowhard.core.domain.StoredMessageRole
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.core.domain.TextGenerationPort
import io.github.artemidius.blowhard.core.domain.TranscriptSaveResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultConversationRunsTest {
    @Test
    fun `two observers share one provider execution`() = runTest {
        var executions = 0
        val runs = controller(
            TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.TextDelta("Hello"), GenerationEvent.Completed)
            },
        )

        val runId = runs.startRun("conversation-1")
        val first = async { runs.snapshots(runId).first { it is RunSnapshot.Succeeded } }
        val second = async { runs.snapshots(runId).first { it is RunSnapshot.Succeeded } }
        advanceUntilIdle()

        assertEquals(1, executions)
        assertEquals(first.await(), second.await())
    }

    @Test
    fun `cancellation produces one immutable terminal snapshot`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flow {
                    emit(GenerationEvent.TextDelta("partial"))
                    awaitCancellation()
                }
            },
        )
        val runId = runs.startRun("conversation-1")
        runCurrent()

        assertEquals(CancelRunResult.ACCEPTED, runs.cancel(runId))
        assertEquals(CancelRunResult.ALREADY_FINISHED, runs.cancel(runId))
        assertEquals("partial", assertIs<RunSnapshot.Cancelled>(runs.snapshots(runId).first()).partialOutput)
    }

    @Test
    fun `events after completion cannot mutate terminal output`() = runTest {
        val runs = controller(
            TextGenerationPort {
                flowOf(
                    GenerationEvent.TextDelta("final"),
                    GenerationEvent.Completed,
                    GenerationEvent.TextDelta("late"),
                )
            },
        )

        val runId = runs.startRun("conversation-1")
        advanceUntilIdle()

        assertEquals("final", assertIs<RunSnapshot.Succeeded>(runs.snapshots(runId).first()).output)
    }

    @Test
    fun `same conversation is exclusive while other conversations remain isolated`() = runTest {
        val runs = controller(
            TextGenerationPort { request ->
                if (request.conversationId.value == "conversation-1") {
                    flow { awaitCancellation() }
                } else {
                    flowOf(GenerationEvent.TextDelta("other"), GenerationEvent.Completed)
                }
            },
        )
        runs.startRun("conversation-1")
        runCurrent()

        val duplicate = runs.start(StartRunRequest(ConversationId("conversation-1"), "Hello"))
        val other = runs.startRun("conversation-2")
        advanceUntilIdle()

        assertEquals(
            StartRunRejection.CONVERSATION_BUSY,
            assertIs<StartRunResult.Rejected>(duplicate).reason,
        )
        assertEquals("other", assertIs<RunSnapshot.Succeeded>(runs.snapshots(other).first()).output)
    }

    @Test
    fun `blank prompts never invoke the provider`() = runTest {
        var executions = 0
        val runs = controller(
            TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.Completed)
            },
        )

        val result = runs.start(StartRunRequest(ConversationId("conversation-1"), " \n "))

        assertEquals(StartRunRejection.BLANK_PROMPT, assertIs<StartRunResult.Rejected>(result).reason)
        assertEquals(0, executions)
    }

    @Test
    fun `accepted live run keeps its verified model when settings change before execution`() = runTest {
        val settings = FakeRunConnectionSettings(readyLiveState())
        var observedTarget: GenerationTarget? = null
        val runs = controller(
            generation = TextGenerationPort { request ->
                observedTarget = request.target
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = settings,
        )

        val result = assertIs<StartRunResult.Started>(
            runs.start(StartRunRequest(ConversationId("live-conversation"), "Hello")),
        )
        settings.mutableState.value = ConnectionSettingsState()
        advanceUntilIdle()

        assertEquals(GenerationTarget.Live("verified-model"), result.target)
        assertEquals(result.target, observedTarget)
    }

    @Test
    fun `accepted run keeps its generation parameters when settings change before execution`() = runTest {
        val acceptedParameters = GenerationParameters(maxOutputTokens = 1_024, temperature = 0.2)
        val settings = FakeRunConnectionSettings(
            readyLiveState().copy(generationParameters = acceptedParameters),
        )
        var observedParameters: GenerationParameters? = null
        val runs = controller(
            generation = TextGenerationPort { request ->
                observedParameters = request.parameters
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = settings,
        )

        runs.start(StartRunRequest(ConversationId("live-conversation"), "Hello"))
        settings.mutableState.value = settings.mutableState.value.copy(
            generationParameters = GenerationParameters(maxOutputTokens = 256, temperature = 0.7),
        )
        advanceUntilIdle()

        assertEquals(acceptedParameters, observedParameters)
    }

    @Test
    fun `stale live selection fails closed before provider execution`() = runTest {
        var executions = 0
        val settings = FakeRunConnectionSettings(
            readyLiveState().copy(connectionStatus = ConnectionStatus.FAILED),
        )
        val runs = controller(
            generation = TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = settings,
        )

        val result = runs.start(StartRunRequest(ConversationId("live-conversation"), "Hello"))
        advanceUntilIdle()

        assertEquals(
            StartRunRejection.LIVE_CONFIGURATION_NOT_READY,
            assertIs<StartRunResult.Rejected>(result).reason,
        )
        assertEquals(0, executions)
    }

    @Test
    fun `vision run resolves one explicitly selected local image`() = runTest {
        val documentId = DocumentId("selected-image")
        val content = byteArrayOf(1, 2, 3)
        var observedBytes: ByteArray? = null
        var observedMediaType: GenerationImageMediaType? = null
        val runs = controller(
            generation = TextGenerationPort { request ->
                observedBytes = request.image?.copyBytes()
                observedMediaType = request.image?.mediaType
                flowOf(GenerationEvent.Completed)
            },
            connectionSettings = FakeRunConnectionSettings(readyLiveState(supportsVision = true)),
            documentProcessingStore = FakeDocumentProcessingStore(
                DocumentSourceLookupResult.Found(imageSource(documentId, content)),
            ),
        )

        val result = runs.start(
            StartRunRequest(
                ConversationId("vision"),
                "Describe this image",
                RunAttachment(documentId),
            ),
        )
        content[0] = 9
        advanceUntilIdle()

        assertIs<StartRunResult.Started>(result)
        assertEquals(listOf<Byte>(1, 2, 3), observedBytes?.toList())
        assertEquals(GenerationImageMediaType.PNG, observedMediaType)
    }

    @Test
    fun `vision capability is required before local content is read`() = runTest {
        val store = FakeDocumentProcessingStore(DocumentSourceLookupResult.Unavailable)
        val runs = controller(
            generation = TextGenerationPort { flowOf(GenerationEvent.Completed) },
            connectionSettings = FakeRunConnectionSettings(readyLiveState()),
            documentProcessingStore = store,
        )

        val result = runs.start(
            StartRunRequest(
                ConversationId("vision"),
                "Describe",
                RunAttachment(DocumentId("selected-image")),
            ),
        )

        assertEquals(
            StartRunRejection.MODEL_DOES_NOT_SUPPORT_VISION,
            assertIs<StartRunResult.Rejected>(result).reason,
        )
        assertEquals(0, store.readCalls)
    }

    @Test
    fun `missing and non-image selections never invoke generation`() = runTest {
        var executions = 0
        val settings = FakeRunConnectionSettings(readyLiveState(supportsVision = true))
        val generation = TextGenerationPort {
            executions += 1
            flowOf(GenerationEvent.Completed)
        }
        val missingRuns = controller(
            generation,
            connectionSettings = settings,
            documentProcessingStore = FakeDocumentProcessingStore(DocumentSourceLookupResult.NotFound),
        )
        val pdfRuns = controller(
            generation,
            connectionSettings = settings,
            documentProcessingStore = FakeDocumentProcessingStore(
                DocumentSourceLookupResult.Found(pdfSource(DocumentId("pdf"))),
            ),
        )

        val missing = missingRuns.start(
            StartRunRequest(ConversationId("missing"), "Describe", RunAttachment(DocumentId("missing"))),
        )
        val pdf = pdfRuns.start(
            StartRunRequest(ConversationId("pdf"), "Describe", RunAttachment(DocumentId("pdf"))),
        )
        advanceUntilIdle()

        assertEquals(
            StartRunRejection.ATTACHMENT_NOT_AVAILABLE,
            assertIs<StartRunResult.Rejected>(missing).reason,
        )
        assertEquals(
            StartRunRejection.ATTACHMENT_NOT_SUPPORTED,
            assertIs<StartRunResult.Rejected>(pdf).reason,
        )
        assertEquals(0, executions)
    }

    @Test
    fun `image lookup failures are safe while cancellation remains cooperative`() = runTest {
        val settings = FakeRunConnectionSettings(readyLiveState(supportsVision = true))
        val unavailableRuns = controller(
            TextGenerationPort { flowOf(GenerationEvent.Completed) },
            connectionSettings = settings,
            documentProcessingStore = FakeDocumentProcessingStore(
                DocumentSourceLookupResult.Unavailable,
                failure = IllegalStateException("private database detail"),
            ),
        )
        val cancelledRuns = controller(
            TextGenerationPort { flowOf(GenerationEvent.Completed) },
            connectionSettings = settings,
            documentProcessingStore = FakeDocumentProcessingStore(
                DocumentSourceLookupResult.Unavailable,
                failure = CancellationException("cancelled"),
            ),
        )
        val request = StartRunRequest(
            ConversationId("vision"),
            "Describe",
            RunAttachment(DocumentId("selected-image")),
        )

        val unavailable = unavailableRuns.start(request)

        assertEquals(
            StartRunRejection.ATTACHMENT_NOT_AVAILABLE,
            assertIs<StartRunResult.Rejected>(unavailable).reason,
        )
        assertFailsWith<CancellationException> { cancelledRuns.start(request) }
    }

    @Test
    fun `close cancels active work and rejects future starts`() = runTest {
        val runs = controller(TextGenerationPort { flow { awaitCancellation() } })
        val runId = runs.startRun("conversation-1")
        runCurrent()

        runs.close()
        val afterClose = runs.start(StartRunRequest(ConversationId("conversation-2"), "Hello"))

        assertIs<RunSnapshot.Cancelled>(runs.snapshots(runId).first())
        assertEquals(StartRunRejection.LIFECYCLE_CLOSED, assertIs<StartRunResult.Rejected>(afterClose).reason)
    }

    @Test
    fun `concurrent cancellation requests still write one terminal state`() = runTest {
        val runs = controller(TextGenerationPort { flow { awaitCancellation() } })
        val runId = runs.startRun("conversation-1")
        runCurrent()

        val first = async { runs.cancel(runId) }
        val second = async { runs.cancel(runId) }
        advanceUntilIdle()

        assertEquals(
            setOf(CancelRunResult.ACCEPTED, CancelRunResult.ALREADY_FINISHED),
            setOf(first.await(), second.await()),
        )
        assertIs<RunSnapshot.Cancelled>(runs.snapshots(runId).first())
    }

    @Test
    fun `cancelling unknown run is a typed result`() = runTest {
        val runs = controller(TextGenerationPort { flowOf(GenerationEvent.Completed) })

        assertEquals(CancelRunResult.NOT_FOUND, runs.cancel(RunId("missing")))
    }

    @Test
    fun `durable run writes bounded checkpoints and terminal output`() = runTest {
        val store = CheckpointRecordingStore()
        val runs = controller(
            generation = TextGenerationPort {
                flowOf(
                    GenerationEvent.TextDelta("small "),
                    GenerationEvent.TextDelta("chunks"),
                    GenerationEvent.Completed,
                )
            },
            conversationStore = store,
            checkpointPolicy = RunCheckpointPolicy(
                minimumOutputCharacters = 100,
                maximumIntervalMilliseconds = 100,
            ),
        )

        val runId = runs.startRun("durable")
        advanceUntilIdle()

        assertEquals(
            listOf(StoredAttemptStatus.QUEUED, StoredAttemptStatus.RUNNING, StoredAttemptStatus.SUCCEEDED),
            store.checkpoints.map { it.transcript.attempts.last().status },
        )
        val checkpoint = store.checkpoints.last()
        assertEquals("Hello", checkpoint.transcript.messages.first().text)
        assertEquals("small chunks", checkpoint.transcript.messages.last().text)
        assertEquals(runId, checkpoint.transcript.attempts.last().runId)
    }

    @Test
    fun `initial checkpoint failure rejects before provider execution`() = runTest {
        var executions = 0
        val store = CheckpointRecordingStore(failAtCheckpoint = 1)
        val runs = controller(
            generation = TextGenerationPort {
                executions += 1
                flowOf(GenerationEvent.Completed)
            },
            conversationStore = store,
        )

        val result = runs.start(StartRunRequest(ConversationId("durable"), "Hello"))
        advanceUntilIdle()

        assertEquals(StartRunRejection.STORAGE_UNAVAILABLE, assertIs<StartRunResult.Rejected>(result).reason)
        assertEquals(0, executions)
    }

    @Test
    fun `stream checkpoint failure stops generation with safe storage failure`() = runTest {
        val store = CheckpointRecordingStore(failAtCheckpoint = 3)
        val runs = controller(
            generation = TextGenerationPort {
                flowOf(GenerationEvent.TextDelta("checkpoint me"), GenerationEvent.Completed)
            },
            conversationStore = store,
            checkpointPolicy = RunCheckpointPolicy(
                minimumOutputCharacters = 1,
                maximumIntervalMilliseconds = 100,
            ),
        )

        val runId = runs.startRun("durable")
        advanceUntilIdle()

        val failure = assertIs<RunSnapshot.Failed>(runs.snapshots(runId).first())
        assertEquals(RunFailureKind.STORAGE_UNAVAILABLE, failure.failure.kind)
        assertEquals("checkpoint me", failure.partialOutput)
        assertEquals(StoredAttemptStatus.RUNNING, store.checkpoints.last().transcript.attempts.last().status)
    }

    @Test
    fun `cancellation checkpoints partial output as terminal`() = runTest {
        val store = CheckpointRecordingStore()
        val runs = controller(
            generation = TextGenerationPort {
                flow {
                    emit(GenerationEvent.TextDelta("partial"))
                    awaitCancellation()
                }
            },
            conversationStore = store,
        )
        val runId = runs.startRun("durable")
        runCurrent()

        assertEquals(CancelRunResult.ACCEPTED, runs.cancel(runId))

        val checkpoint = store.checkpoints.last()
        assertEquals(StoredAttemptStatus.CANCELLED, checkpoint.transcript.attempts.last().status)
        assertEquals("partial", checkpoint.transcript.messages.last().text)
    }

    @Test
    fun `new durable attempt preserves existing transcript`() = runTest {
        val existing = existingCheckpoint()
        val store = CheckpointRecordingStore(existing.conversation, existing.transcript)
        val runs = controller(
            generation = TextGenerationPort { flowOf(GenerationEvent.TextDelta("new"), GenerationEvent.Completed) },
            conversationStore = store,
        )

        runs.startRun("durable")
        advanceUntilIdle()

        val checkpoint = store.checkpoints.last()
        assertEquals("Existing title", checkpoint.conversation.title)
        assertEquals(
            listOf("old question", "old answer", "Hello", "new"),
            checkpoint.transcript.messages.map {
                it.text
            },
        )
        assertEquals(2, checkpoint.transcript.attempts.size)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        generation: TextGenerationPort,
        maxConcurrentRuns: Int = 2,
        connectionSettings: ConnectionSettings? = null,
        conversationStore: ConversationStore? = null,
        checkpointPolicy: RunCheckpointPolicy = RunCheckpointPolicy(),
        documentProcessingStore: DocumentProcessingStore? = null,
    ): DefaultConversationRuns = DefaultConversationRuns(
        generation = generation,
        idFactory = SequentialRunIdFactory(),
        clock = IncrementingClock(),
        coroutineContext = StandardTestDispatcher(testScheduler),
        policy = RunExecutionPolicy(maxConcurrentRuns),
        connectionSettings = connectionSettings,
        conversationStore = conversationStore,
        checkpointPolicy = checkpointPolicy,
        documentProcessingStore = documentProcessingStore,
    )
}

private class FakeDocumentProcessingStore(
    private val result: DocumentSourceLookupResult,
    private val failure: Throwable? = null,
) : DocumentProcessingStore {
    var readCalls: Int = 0

    override suspend fun readSource(id: DocumentId): DocumentSourceLookupResult {
        readCalls += 1
        failure?.let { throw it }
        return result
    }

    override suspend fun saveExtraction(extraction: DocumentExtraction): DocumentExtractionSaveResult =
        DocumentExtractionSaveResult.UNAVAILABLE

    override suspend fun findExtraction(id: DocumentId): DocumentExtractionStoreLookupResult =
        DocumentExtractionStoreLookupResult.Unavailable

    override suspend fun close(): Unit = Unit
}

private fun imageSource(id: DocumentId, content: ByteArray): StoredDocumentSource = StoredDocumentSource(
    document = io.github.artemidius.blowhard.core.api.ImportedDocument(
        id,
        "image.png",
        content.size.toLong(),
        1,
        DocumentContentDescriptor.Image(DocumentFormat.PNG, 1, 1),
    ),
    content = content,
)

private fun pdfSource(id: DocumentId): StoredDocumentSource {
    val content = byteArrayOf(1)
    return StoredDocumentSource(
        document = io.github.artemidius.blowhard.core.api.ImportedDocument(
            id,
            "document.pdf",
            content.size.toLong(),
            1,
            DocumentContentDescriptor.Pdf(1),
        ),
        content = content,
    )
}

private data class RecordedCheckpoint(val conversation: StoredConversation, val transcript: StoredTranscript)

private class CheckpointRecordingStore(
    initialConversation: StoredConversation? = null,
    initialTranscript: StoredTranscript? = null,
    private val failAtCheckpoint: Int? = null,
) : ConversationStore {
    private var conversation = initialConversation
    private var transcript = initialTranscript
    private var checkpointCalls = 0
    val checkpoints = mutableListOf<RecordedCheckpoint>()

    override suspend fun find(id: ConversationId): StoredConversation? = conversation?.takeIf { it.id == id }

    override suspend fun loadTranscript(id: ConversationId): StoredTranscript? = transcript?.takeIf {
        it.conversationId ==
            id
    }

    override suspend fun saveCheckpoint(conversation: StoredConversation, transcript: StoredTranscript) {
        checkpointCalls += 1
        if (checkpointCalls == failAtCheckpoint) error("synthetic storage failure")
        val storedConversation = this.conversation?.takeIf { it.id == conversation.id }?.let {
            conversation.copy(createdAtEpochMilliseconds = it.createdAtEpochMilliseconds)
        } ?: conversation
        this.conversation = storedConversation
        this.transcript = transcript
        checkpoints += RecordedCheckpoint(storedConversation, transcript)
    }

    override suspend fun save(conversation: StoredConversation): Unit = unsupported()

    override suspend fun list(): List<StoredConversation> = unsupported()

    override suspend fun saveTranscript(transcript: StoredTranscript): TranscriptSaveResult = unsupported()

    override suspend fun recoverInFlightAttempts(recoveredAt: RunTimestamp): ConversationRecoveryResult = unsupported()

    override suspend fun delete(id: ConversationId): Boolean = unsupported()

    override suspend fun close(): Unit = Unit

    private fun unsupported(): Nothing = error("Not used by run checkpoint tests")
}

private fun existingCheckpoint(): RecordedCheckpoint {
    val conversationId = ConversationId("durable")
    val userId = MessageId("old-user")
    val assistantId = MessageId("old-assistant")
    return RecordedCheckpoint(
        conversation = StoredConversation(conversationId, "Existing title", 1, 2),
        transcript = StoredTranscript(
            conversationId = conversationId,
            messages = listOf(
                StoredMessage(userId, 0, StoredMessageRole.USER, "old question", 1),
                StoredMessage(assistantId, 1, StoredMessageRole.ASSISTANT, "old answer", 2),
            ),
            attempts = listOf(
                StoredGenerationAttempt(
                    runId = RunId("old-run"),
                    userMessageId = userId,
                    assistantMessageId = assistantId,
                    target = GenerationTarget.Demo,
                    parameters = GenerationParameters(),
                    status = StoredAttemptStatus.SUCCEEDED,
                    createdAtEpochMilliseconds = 1,
                    updatedAtEpochMilliseconds = 2,
                ),
            ),
        ),
    )
}

private class FakeRunConnectionSettings(initial: ConnectionSettingsState) : ConnectionSettings {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ConnectionSettingsState> = mutableState

    override suspend fun refreshCredentialStatus() = Unit

    override suspend fun replaceCredential(secret: String): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun deleteCredential(): CredentialUpdateResult = CredentialUpdateResult.SUCCESS

    override suspend fun testConnection(): ConnectionTestResult = ConnectionTestResult.Failure(
        ConnectionTestFailureKind.UNEXPECTED,
    )

    override suspend fun selectModel(modelId: String): ModelSelectionResult = ModelSelectionResult.UPDATED

    override suspend fun updateGenerationParameters(parameters: GenerationParameters) = Unit

    override suspend fun acknowledgeLiveDataDisclosure() = Unit

    override suspend fun selectMode(mode: GenerationMode): ModeSelectionResult = ModeSelectionResult.UPDATED
}

private fun readyLiveState(supportsVision: Boolean = false): ConnectionSettingsState = ConnectionSettingsState(
    mode = GenerationMode.LIVE,
    credentialStatus = CredentialStatus.CONFIGURED,
    connectionStatus = ConnectionStatus.READY,
    models = listOf(SelectableModel("verified-model", 32_000, supportsVision)),
    selectedModelId = "verified-model",
    liveDataDisclosureAccepted = true,
)

internal suspend fun DefaultConversationRuns.startRun(conversation: String): RunId {
    val result = start(StartRunRequest(ConversationId(conversation), "Hello"))
    return assertIs<StartRunResult.Started>(result).runId
}

internal suspend fun DefaultConversationRuns.snapshots(runId: RunId): kotlinx.coroutines.flow.Flow<RunSnapshot> =
    assertIs<ObserveRunResult.Found>(observe(runId)).snapshots

internal class IncrementingClock : RunClock {
    private var next = 1_000L

    override fun now(): RunTimestamp = RunTimestamp(next++)
}
