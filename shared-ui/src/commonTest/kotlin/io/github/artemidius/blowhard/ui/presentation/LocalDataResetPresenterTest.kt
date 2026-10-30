package io.github.artemidius.blowhard.ui.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDataResetPresenterTest {
    @Test
    fun `reset requires explicit confirmation`() = runTest {
        var calls = 0
        val presenter = presenter {
            calls += 1
            LocalDataResetResult.ERASED
        }

        presenter.dispatch(LocalDataResetAction.Requested)
        runCurrent()
        assertTrue(presenter.state.value.confirmationRequested)
        assertEquals(0, calls)

        presenter.dispatch(LocalDataResetAction.Cancelled)
        runCurrent()
        assertEquals(LocalDataResetUiState(), presenter.state.value)
        assertEquals(0, calls)
        presenter.close()
    }

    @Test
    fun `confirmed reset publishes progress and success`() = runTest {
        val release = CompletableDeferred<Unit>()
        val presenter = presenter {
            release.await()
            LocalDataResetResult.ERASED
        }

        presenter.confirm()
        runCurrent()
        assertTrue(presenter.state.value.inProgress)
        assertFalse(presenter.state.value.confirmationRequested)

        release.complete(Unit)
        runCurrent()
        assertEquals(LocalDataResetUiState(outcome = LocalDataResetResult.ERASED), presenter.state.value)
        presenter.close()
    }

    @Test
    fun `queued duplicate confirmation cannot repeat erasure`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val presenter = presenter {
            calls += 1
            release.await()
            LocalDataResetResult.ERASED
        }

        presenter.dispatch(LocalDataResetAction.Requested)
        presenter.dispatch(LocalDataResetAction.Confirmed)
        presenter.dispatch(LocalDataResetAction.Confirmed)
        runCurrent()
        assertEquals(1, calls)

        release.complete(Unit)
        runCurrent()
        assertEquals(1, calls)
        presenter.close()
    }

    @Test
    fun `host failure and exception publish the same sanitized outcome`() = runTest {
        val failed = presenter { LocalDataResetResult.FAILED }
        failed.confirm()
        runCurrent()
        assertEquals(LocalDataResetResult.FAILED, failed.state.value.outcome)
        failed.close()

        val crashed = presenter { error("private filesystem detail") }
        crashed.confirm()
        runCurrent()
        assertEquals(LocalDataResetResult.FAILED, crashed.state.value.outcome)
        crashed.close()
    }

    @Test
    fun `new request and dismissal clear old outcome`() = runTest {
        val presenter = presenter { LocalDataResetResult.ERASED }
        presenter.confirm()
        runCurrent()
        assertEquals(LocalDataResetResult.ERASED, presenter.state.value.outcome)

        presenter.dispatch(LocalDataResetAction.OutcomeDismissed)
        presenter.dispatch(LocalDataResetAction.Requested)
        runCurrent()
        assertEquals(LocalDataResetUiState(confirmationRequested = true), presenter.state.value)
        presenter.close()
    }

    private fun kotlinx.coroutines.test.TestScope.presenter(
        erase: suspend () -> LocalDataResetResult,
    ): LocalDataResetPresenter = LocalDataResetPresenter(
        eraser = LocalDataEraser { erase() },
        coroutineContext = StandardTestDispatcher(testScheduler),
    )
}

private fun LocalDataResetPresenter.confirm() {
    dispatch(LocalDataResetAction.Requested)
    dispatch(LocalDataResetAction.Confirmed)
}
