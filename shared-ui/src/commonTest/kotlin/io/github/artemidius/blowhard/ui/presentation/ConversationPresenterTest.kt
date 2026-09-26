package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.CancelRunResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.ConversationRuns
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.ObserveRunResult
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTimestamp
import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.api.StartRunResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationPresenterTest {
    @Test
    fun `workspace actions update only presentation state`() = runTest {
        val fixture = PresenterFixture(this)

        fixture.presenter.dispatch(ConversationAction.NewConversation)
        fixture.presenter.dispatch(ConversationAction.ComposerChanged("hello"))
        fixture.presenter.dispatch(ConversationAction.ScenarioChanged(DemoScenario.RATE_LIMITED))
        runCurrent()

        val state = fixture.presenter.state.value
        assertEquals(2, state.conversations.size)
        assertEquals(ConversationId("conversation-2"), state.selectedConversationId)
        assertEquals("hello", state.composer)
        assertEquals(DemoScenario.RATE_LIMITED, state.scenario)
        fixture.close()
    }

    @Test
    fun `two send actions create one run and one observer`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.presenter.dispatch(ConversationAction.ComposerChanged("Explain the lifecycle"))
        fixture.presenter.dispatch(ConversationAction.Send)
        fixture.presenter.dispatch(ConversationAction.Send)
        runCurrent()

        val state = fixture.presenter.state.value
        assertEquals(1, fixture.runs.startRequests.size)
        assertEquals(1, fixture.runs.observeCalls)
        assertEquals(listOf(DemoScenario.SUCCESS), fixture.scenarios.map { it.second })
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), state.selectedConversation.messages.map { it.role })
        assertEquals(GenerationTarget.Demo, state.selectedConversation.latestTarget)
        assertEquals("Explain the lifecycle", state.selectedConversation.title)
        assertFalse(state.canSend)
        assertTrue(state.canStop)
        fixture.close()
    }

    @Test
    fun `accepted live target labels the conversation and both messages`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.runs.startTarget = GenerationTarget.Live("verified-model")

        fixture.start("Use live routing")

        val conversation = fixture.presenter.state.value.selectedConversation
        assertEquals(GenerationTarget.Live("verified-model"), conversation.latestTarget)
        assertEquals(
            listOf(GenerationTarget.Live("verified-model"), GenerationTarget.Live("verified-model")),
            conversation.messages.map { it.target },
        )
        assertEquals(listOf(RunId("fake-run-1"), RunId("fake-run-1")), conversation.messages.map { it.attemptId })
        fixture.close()
    }

    @Test
    fun `streaming and terminal snapshots update one assistant message`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Stream")
        val run = fixture.runs.startedRun()

        fixture.runs.emit(RunSnapshot.Running(run, DEFAULT_CONVERSATION, NOW, "partial"))
        runCurrent()
        assertEquals("partial", fixture.presenter.state.value.selectedConversation.messages.last().text)
        assertEquals(ConversationRunState.STREAMING, fixture.presenter.state.value.selectedConversation.runState)

        fixture.runs.emit(RunSnapshot.Succeeded(run, DEFAULT_CONVERSATION, NOW, "complete"))
        runCurrent()
        val completed = fixture.presenter.state.value
        assertEquals("complete", completed.selectedConversation.messages.last().text)
        assertEquals(MessageDelivery.COMPLETE, completed.selectedConversation.messages.last().delivery)
        assertFalse(completed.canStop)
        fixture.close()
    }

    @Test
    fun `regeneration preserves the prior result and starts a distinct attempt with current routing`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Explain immutable attempts")
        val firstRun = fixture.runs.startedRun()
        fixture.runs.emit(RunSnapshot.Succeeded(firstRun, DEFAULT_CONVERSATION, NOW, "first result"))
        runCurrent()
        val firstResponse = fixture.presenter.state.value.selectedConversation.messages.last()

        fixture.presenter.dispatch(ConversationAction.ComposerChanged("draft stays here"))
        fixture.runs.startTarget = GenerationTarget.Live("current-model")
        fixture.presenter.dispatch(ConversationAction.Regenerate(firstResponse.id))
        runCurrent()

        val messages = fixture.presenter.state.value.selectedConversation.messages
        assertEquals(
            listOf("Explain immutable attempts", "Explain immutable attempts"),
            fixture.runs.startRequests.map {
                it.prompt
            },
        )
        assertEquals(4, messages.size)
        assertEquals("first result", messages[1].text)
        assertEquals("", messages[3].text)
        assertEquals(
            listOf(firstRun, firstRun, RunId("fake-run-2"), RunId("fake-run-2")),
            messages.map { it.attemptId },
        )
        assertEquals(
            listOf(
                GenerationTarget.Demo,
                GenerationTarget.Demo,
                GenerationTarget.Live("current-model"),
                GenerationTarget.Live("current-model"),
            ),
            messages.map { it.target },
        )
        assertEquals("draft stays here", fixture.presenter.state.value.composer)
        assertEquals(2, fixture.runs.observeCalls)
        fixture.close()
    }

    @Test
    fun `regeneration is serialized and cannot duplicate an active attempt`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Only once")
        val firstRun = fixture.runs.startedRun()
        fixture.runs.emit(RunSnapshot.Succeeded(firstRun, DEFAULT_CONVERSATION, NOW, "ready"))
        runCurrent()
        val responseId = fixture.presenter.state.value.selectedConversation.messages.last().id

        fixture.presenter.dispatch(ConversationAction.Regenerate(responseId))
        fixture.presenter.dispatch(ConversationAction.Regenerate(responseId))
        runCurrent()

        assertEquals(2, fixture.runs.startRequests.size)
        assertEquals(4, fixture.presenter.state.value.selectedConversation.messages.size)
        fixture.close()
    }

    @Test
    fun `retry-safe terminal failure exposes regeneration`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Try safely")
        val firstRun = fixture.runs.startedRun()
        fixture.runs.emit(
            RunSnapshot.Failed(
                runId = firstRun,
                conversationId = DEFAULT_CONVERSATION,
                updatedAt = NOW,
                partialOutput = "kept",
                failure = RunFailure(
                    kind = RunFailureKind.UNAVAILABLE,
                    retryAdvice = RunRetryAdvice.EXPLICIT_NEW_ATTEMPT,
                ),
            ),
        )
        runCurrent()
        val retryableResponse = fixture.presenter.state.value.selectedConversation.messages.last()
        assertTrue(retryableResponse.canRegenerate)

        fixture.presenter.dispatch(ConversationAction.Regenerate(retryableResponse.id))
        runCurrent()
        assertEquals(2, fixture.runs.startRequests.size)
        fixture.close()
    }

    @Test
    fun `unsafe terminal failure does not expose regeneration`() = runTest {
        val unsafeFixture = PresenterFixture(this)
        unsafeFixture.start("Do not repeat")
        val unsafeRun = unsafeFixture.runs.startedRun()
        unsafeFixture.runs.emit(
            RunSnapshot.Failed(
                runId = unsafeRun,
                conversationId = DEFAULT_CONVERSATION,
                updatedAt = NOW,
                partialOutput = "kept",
                failure = RunFailure(RunFailureKind.UNEXPECTED),
            ),
        )
        runCurrent()
        val unsafeResponse = unsafeFixture.presenter.state.value.selectedConversation.messages.last()
        assertFalse(unsafeResponse.canRegenerate)

        unsafeFixture.presenter.dispatch(ConversationAction.Regenerate(unsafeResponse.id))
        runCurrent()
        assertEquals(1, unsafeFixture.runs.startRequests.size)
        unsafeFixture.close()
    }

    @Test
    fun `rejected regeneration preserves history and composer draft`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Preserve me")
        val firstRun = fixture.runs.startedRun()
        fixture.runs.emit(RunSnapshot.Succeeded(firstRun, DEFAULT_CONVERSATION, NOW, "original"))
        runCurrent()
        val before = fixture.presenter.state.value.selectedConversation.messages
        val responseId = before.last().id
        fixture.presenter.dispatch(ConversationAction.ComposerChanged("unrelated draft"))
        fixture.runs.startResult = StartRunResult.Rejected(StartRunRejection.LIVE_CONFIGURATION_NOT_READY)

        fixture.presenter.dispatch(ConversationAction.Regenerate(responseId))
        runCurrent()

        val state = fixture.presenter.state.value
        assertEquals(before, state.selectedConversation.messages)
        assertEquals("unrelated draft", state.composer)
        assertEquals(
            "Live configuration is not ready. Reverify the credential and selected model.",
            state.notice?.message,
        )
        fixture.close()
    }

    @Test
    fun `stop cancels the selected run and preserves partial output`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Stop this")
        val run = fixture.runs.startedRun()
        fixture.runs.emit(RunSnapshot.Running(run, DEFAULT_CONVERSATION, NOW, "kept"))
        runCurrent()

        fixture.presenter.dispatch(ConversationAction.Stop)
        runCurrent()

        val state = fixture.presenter.state.value
        assertEquals(listOf(run), fixture.runs.cancelledRuns)
        assertEquals("kept", state.selectedConversation.messages.last().text)
        assertEquals(MessageDelivery.CANCELLED, state.selectedConversation.messages.last().delivery)
        assertEquals(ConversationRunState.CANCELLED, state.selectedConversation.runState)
        fixture.close()
    }

    @Test
    fun `classified failure shows safe copy without provider reference`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("Fail safely")
        val run = fixture.runs.startedRun()
        fixture.runs.emit(
            RunSnapshot.Failed(
                runId = run,
                conversationId = DEFAULT_CONVERSATION,
                updatedAt = NOW,
                partialOutput = "safe partial",
                failure = RunFailure(RunFailureKind.UNEXPECTED, "secret-provider-payload"),
            ),
        )
        runCurrent()

        val state = fixture.presenter.state.value
        assertEquals("safe partial", state.selectedConversation.messages.last().text)
        assertEquals(MessageDelivery.FAILED, state.selectedConversation.messages.last().delivery)
        assertFalse(state.notice!!.message.contains("secret-provider-payload"))
        fixture.close()
    }

    @Test
    fun `start rejection restores composer and leaves no messages`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.runs.startResult = StartRunResult.Rejected(StartRunRejection.CONVERSATION_BUSY)

        fixture.presenter.dispatch(ConversationAction.ComposerChanged("retry me"))
        fixture.presenter.dispatch(ConversationAction.Send)
        runCurrent()

        val state = fixture.presenter.state.value
        assertEquals("retry me", state.composer)
        assertTrue(state.selectedConversation.messages.isEmpty())
        assertEquals(ConversationRunState.IDLE, state.selectedConversation.runState)
        assertTrue(state.notice!!.message.contains("active run"))
        fixture.close()
    }

    @Test
    fun `missing observation becomes a non-sensitive failed message`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.runs.observationMissing = true

        fixture.start("Observe me")

        val state = fixture.presenter.state.value
        assertEquals(ConversationRunState.FAILED, state.selectedConversation.runState)
        assertEquals(MessageDelivery.FAILED, state.selectedConversation.messages.last().delivery)
        assertEquals("The accepted run could not be observed.", state.notice?.message)
        fixture.close()
    }

    @Test
    fun `conversation runs remain isolated while selection changes`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("First")
        val firstRun = fixture.runs.startedRun()
        fixture.presenter.dispatch(ConversationAction.NewConversation)
        fixture.presenter.dispatch(ConversationAction.ComposerChanged("Second"))
        fixture.presenter.dispatch(ConversationAction.Send)
        runCurrent()
        val secondRun = fixture.runs.startedRun()

        fixture.runs.emit(secondRun, RunSnapshot.Running(secondRun, ConversationId("conversation-2"), NOW, "two"))
        fixture.runs.emit(firstRun, RunSnapshot.Succeeded(firstRun, DEFAULT_CONVERSATION, NOW, "one"))
        runCurrent()

        val conversations = fixture.presenter.state.value.conversations
        assertEquals("one", conversations[0].messages.last().text)
        assertEquals("two", conversations[1].messages.last().text)
        fixture.close()
    }

    @Test
    fun `background failure feedback stays with its conversation`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("First")
        val firstRun = fixture.runs.startedRun()
        fixture.presenter.dispatch(ConversationAction.NewConversation)
        runCurrent()

        fixture.runs.emit(
            firstRun,
            RunSnapshot.Failed(
                runId = firstRun,
                conversationId = DEFAULT_CONVERSATION,
                updatedAt = NOW,
                partialOutput = "partial",
                failure = RunFailure(RunFailureKind.UNAVAILABLE),
            ),
        )
        runCurrent()

        assertEquals(null, fixture.presenter.state.value.notice)
        fixture.presenter.dispatch(ConversationAction.ConversationSelected(DEFAULT_CONVERSATION))
        runCurrent()
        assertEquals("The provider is temporarily unavailable.", fixture.presenter.state.value.notice?.message)
        fixture.close()
    }

    @Test
    fun `scenario preparation failure does not call application API`() = runTest {
        val runs = FakeConversationRuns()
        val presenter = ConversationPresenter(
            runs = runs,
            scenarioSelection = DemoScenarioSelection { _, _ -> error("private setup failure") },
            clipboardWriter = ClipboardWriter { ClipboardWriteResult.COPIED },
            coroutineContext = StandardTestDispatcher(testScheduler),
        )
        presenter.dispatch(ConversationAction.ComposerChanged("hello"))
        presenter.dispatch(ConversationAction.Send)
        runCurrent()

        assertTrue(runs.startRequests.isEmpty())
        assertEquals("hello", presenter.state.value.composer)
        assertEquals("The run could not be started safely.", presenter.state.value.notice?.message)
        presenter.close()
    }

    @Test
    fun `all start rejections restore input with safe feedback`() = runTest {
        val expectedCopy = mapOf(
            StartRunRejection.BLANK_PROMPT to "Enter a message before starting generation.",
            StartRunRejection.CONVERSATION_BUSY to "This conversation already has an active run.",
            StartRunRejection.LIVE_CONFIGURATION_NOT_READY to
                "Live configuration is not ready. Reverify the credential and selected model.",
            StartRunRejection.LIFECYCLE_CLOSED to "The application is shutting down and cannot start another run.",
        )
        expectedCopy.forEach { (rejection, copy) ->
            val fixture = PresenterFixture(this)
            fixture.runs.startResult = StartRunResult.Rejected(rejection)
            fixture.start("preserved")
            assertEquals("preserved", fixture.presenter.state.value.composer)
            assertEquals(copy, fixture.presenter.state.value.notice?.message)
            fixture.close()
        }
    }

    @Test
    fun `selection validates identity and notice can be dismissed`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.runs.startResult = StartRunResult.Rejected(StartRunRejection.CONVERSATION_BUSY)
        fixture.start("notice")
        fixture.presenter.dispatch(ConversationAction.ConversationSelected(ConversationId("missing")))
        fixture.presenter.dispatch(ConversationAction.NoticeDismissed)
        runCurrent()

        assertEquals(DEFAULT_CONVERSATION, fixture.presenter.state.value.selectedConversationId)
        assertEquals(null, fixture.presenter.state.value.notice)
        fixture.presenter.dispatch(ConversationAction.NewConversation)
        runCurrent()
        fixture.presenter.dispatch(ConversationAction.ConversationSelected(DEFAULT_CONVERSATION))
        runCurrent()
        assertEquals(DEFAULT_CONVERSATION, fixture.presenter.state.value.selectedConversationId)
        fixture.close()
    }

    @Test
    fun `missing cancellation fails safely and stop without a run is ignored`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.presenter.dispatch(ConversationAction.Stop)
        fixture.start("cancel")
        fixture.runs.cancelResult = CancelRunResult.NOT_FOUND
        fixture.presenter.dispatch(ConversationAction.Stop)
        runCurrent()

        assertEquals(ConversationRunState.FAILED, fixture.presenter.state.value.selectedConversation.runState)
        assertEquals("The active run could not be stopped safely.", fixture.presenter.state.value.notice?.message)
        fixture.close()
    }

    @Test
    fun `application exceptions become generic feedback`() = runTest {
        val startFixture = PresenterFixture(this)
        startFixture.runs.startThrows = true
        startFixture.start("start")
        assertEquals("The run could not be started safely.", startFixture.presenter.state.value.notice?.message)
        startFixture.close()

        val observeFixture = PresenterFixture(this)
        observeFixture.runs.observeThrows = true
        observeFixture.start("observe")
        assertEquals("The accepted run could not be observed.", observeFixture.presenter.state.value.notice?.message)
        observeFixture.close()

        val cancelFixture = PresenterFixture(this)
        cancelFixture.start("cancel")
        cancelFixture.runs.cancelThrows = true
        cancelFixture.presenter.dispatch(ConversationAction.Stop)
        runCurrent()
        assertEquals("The active run could not be stopped safely.", cancelFixture.presenter.state.value.notice?.message)
        cancelFixture.close()
    }

    @Test
    fun `snapshots with mismatched identities cannot mutate active message`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("identity")
        val activeRun = fixture.runs.startedRun()
        fixture.runs.emit(
            activeRun,
            RunSnapshot.Running(RunId("other-run"), DEFAULT_CONVERSATION, NOW, "wrong run"),
        )
        runCurrent()
        fixture.runs.emit(
            activeRun,
            RunSnapshot.Running(activeRun, ConversationId("other-conversation"), NOW, "wrong conversation"),
        )
        runCurrent()

        assertEquals("", fixture.presenter.state.value.selectedConversation.messages.last().text)
        fixture.close()
    }

    @Test
    fun `state rejects a selected conversation that is absent`() {
        assertFailsWith<IllegalArgumentException> {
            ConversationUiState(
                conversations = listOf(ConversationUiModel(DEFAULT_CONVERSATION, "Present")),
                selectedConversationId = ConversationId("missing"),
            )
        }
    }

    @Test
    fun `copy action writes exact Unicode text and reports success`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.start("copy")
        val run = fixture.runs.startedRun()
        val output = "Grüße aus Wien — こんにちは"
        fixture.runs.emit(RunSnapshot.Succeeded(run, DEFAULT_CONVERSATION, NOW, output))
        runCurrent()
        val messageId = fixture.presenter.state.value.selectedConversation.messages.last().id

        fixture.presenter.dispatch(ConversationAction.CopyMessage(messageId))
        runCurrent()

        assertEquals(listOf(output), fixture.copiedTexts)
        assertEquals("Message copied to the clipboard.", fixture.presenter.state.value.notice?.message)
        fixture.close()
    }

    @Test
    fun `unavailable clipboard is safe and unknown message is ignored`() = runTest {
        val fixture = PresenterFixture(this)
        fixture.clipboardResult = ClipboardWriteResult.UNAVAILABLE
        fixture.start("copy")
        val userMessageId = fixture.presenter.state.value.selectedConversation.messages.first().id
        fixture.presenter.dispatch(ConversationAction.CopyMessage("unknown"))
        fixture.presenter.dispatch(ConversationAction.CopyMessage(userMessageId))
        runCurrent()

        assertEquals(listOf("copy"), fixture.copiedTexts)
        assertEquals(
            "The clipboard is unavailable in this desktop session.",
            fixture.presenter.state.value.notice?.message,
        )
        fixture.close()
    }

    private suspend fun PresenterFixture.start(prompt: String) {
        presenter.dispatch(ConversationAction.ComposerChanged(prompt))
        presenter.dispatch(ConversationAction.Send)
        testScope.runCurrent()
    }
}

private class PresenterFixture(val testScope: kotlinx.coroutines.test.TestScope) {
    val runs = FakeConversationRuns()
    val scenarios = mutableListOf<Pair<ConversationId, DemoScenario>>()
    val copiedTexts = mutableListOf<String>()
    var clipboardResult = ClipboardWriteResult.COPIED
    val presenter = ConversationPresenter(
        runs = runs,
        scenarioSelection = DemoScenarioSelection { conversationId, scenario ->
            scenarios += conversationId to scenario
        },
        clipboardWriter = ClipboardWriter { text ->
            copiedTexts += text
            clipboardResult
        },
        coroutineContext = StandardTestDispatcher(testScope.testScheduler),
    )

    fun close() = presenter.close()
}

private class FakeConversationRuns : ConversationRuns {
    val startRequests = mutableListOf<StartRunRequest>()
    val cancelledRuns = mutableListOf<RunId>()
    var startResult: StartRunResult? = null
    var observationMissing = false
    var startThrows = false
    var observeThrows = false
    var cancelThrows = false
    var cancelResult: CancelRunResult? = null
    var startTarget: GenerationTarget = GenerationTarget.Demo
    var observeCalls = 0
    private val snapshots = mutableMapOf<RunId, MutableStateFlow<RunSnapshot>>()

    override suspend fun start(request: StartRunRequest): StartRunResult {
        if (startThrows) error("private start failure")
        startRequests += request
        startResult?.let { return it }
        val runId = RunId("fake-run-${startRequests.size}")
        snapshots[runId] = MutableStateFlow(RunSnapshot.Queued(runId, request.conversationId, NOW))
        return StartRunResult.Started(runId, startTarget)
    }

    override suspend fun observe(runId: RunId): ObserveRunResult {
        if (observeThrows) error("private observe failure")
        observeCalls += 1
        if (observationMissing) return ObserveRunResult.NotFound
        return snapshots[runId]?.let { ObserveRunResult.Found(it) } ?: ObserveRunResult.NotFound
    }

    override suspend fun cancel(runId: RunId): CancelRunResult {
        if (cancelThrows) error("private cancel failure")
        cancelledRuns += runId
        val configuredResult = cancelResult
        val current = snapshots[runId]?.value
        return when {
            configuredResult != null -> configuredResult

            current == null -> CancelRunResult.NOT_FOUND

            else -> {
                val output = (current as? RunSnapshot.Running)?.output.orEmpty()
                snapshots.getValue(runId).value = RunSnapshot.Cancelled(runId, current.conversationId, NOW, output)
                CancelRunResult.ACCEPTED
            }
        }
    }

    fun startedRun(): RunId = RunId("fake-run-${startRequests.size}")

    fun emit(snapshot: RunSnapshot) = emit(snapshot.runId, snapshot)

    fun emit(runId: RunId, snapshot: RunSnapshot) {
        snapshots.getValue(runId).value = snapshot
    }
}

private val DEFAULT_CONVERSATION = ConversationId("conversation-1")
private val NOW = RunTimestamp(1L)
