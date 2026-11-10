package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.GenerationParameters
import io.github.artemidius.blowhard.core.api.GenerationTarget
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunInspection
import io.github.artemidius.blowhard.core.api.RunInspectionEvent
import io.github.artemidius.blowhard.core.api.RunInspectionResult
import io.github.artemidius.blowhard.core.api.RunInspector
import io.github.artemidius.blowhard.core.api.RunTimestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RunInspectorPresenterTest {
    @Test
    fun `new selection replaces an unfinished lookup`() = runTest {
        val slow = RunId("slow")
        val available = inspection(RunId("available"))
        val releaseSlowLookup = CompletableDeferred<Unit>()
        val presenter = presenter(
            RunInspector { runId ->
                if (runId == slow) releaseSlowLookup.await()
                RunInspectionResult.Found(if (runId == slow) inspection(slow) else available)
            },
        )

        presenter.dispatch(RunInspectorAction.Open(slow))
        runCurrent()
        assertEquals(RunInspectorUiState.Loading(slow), presenter.state.value)

        presenter.dispatch(RunInspectorAction.Open(available.runId))
        runCurrent()
        assertEquals(available, assertIs<RunInspectorUiState.Ready>(presenter.state.value).inspection)

        releaseSlowLookup.complete(Unit)
        runCurrent()
        assertEquals(available, assertIs<RunInspectorUiState.Ready>(presenter.state.value).inspection)
        presenter.close()
    }

    @Test
    fun `open and refresh load one content-free inspection`() = runTest {
        var calls = 0
        val expected = inspection(RunId("available"))
        val presenter = presenter(
            RunInspector {
                calls += 1
                RunInspectionResult.Found(expected)
            },
        )

        presenter.dispatch(RunInspectorAction.Refresh)
        presenter.dispatch(RunInspectorAction.Open(expected.runId))
        runCurrent()
        assertEquals(expected, assertIs<RunInspectorUiState.Ready>(presenter.state.value).inspection)

        presenter.dispatch(RunInspectorAction.Refresh)
        runCurrent()
        assertEquals(expected, assertIs<RunInspectorUiState.Ready>(presenter.state.value).inspection)
        assertEquals(2, calls)
        presenter.close()
    }

    @Test
    fun `missing and failed lookups expose safe typed states`() = runTest {
        val missing = RunId("missing")
        val failed = RunId("failed")
        val presenter = presenter(
            RunInspector { runId ->
                if (runId == missing) RunInspectionResult.NotFound else error("sensitive provider detail")
            },
        )

        presenter.dispatch(RunInspectorAction.Open(missing))
        runCurrent()
        assertEquals(RunInspectorUiState.NotFound(missing), presenter.state.value)

        presenter.dispatch(RunInspectorAction.Open(failed))
        runCurrent()
        assertEquals(RunInspectorUiState.Failed(failed), presenter.state.value)
        presenter.close()
    }

    private fun kotlinx.coroutines.test.TestScope.presenter(inspector: RunInspector): RunInspectorPresenter =
        RunInspectorPresenter(inspector, StandardTestDispatcher(testScheduler))
}

private fun inspection(runId: RunId): RunInspection = RunInspection(
    runId = runId,
    conversationId = ConversationId("conversation"),
    target = GenerationTarget.Demo,
    parameters = GenerationParameters(),
    events = listOf(RunInspectionEvent.Accepted(RunTimestamp(1))),
)
