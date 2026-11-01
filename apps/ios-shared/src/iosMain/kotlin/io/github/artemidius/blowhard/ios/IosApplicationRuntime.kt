package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.mobile.IosCredentialStores
import io.github.artemidius.blowhard.composition.ApplicationSessionCloser
import io.github.artemidius.blowhard.composition.ApplicationSessionFactory
import io.github.artemidius.blowhard.composition.ApplicationSessionResetResult
import io.github.artemidius.blowhard.composition.ReplaceableApplicationSession
import io.github.artemidius.blowhard.ui.presentation.LocalDataEraser
import io.github.artemidius.blowhard.ui.presentation.LocalDataResetPresenter
import io.github.artemidius.blowhard.ui.presentation.LocalDataResetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Retains and safely replaces application-owned services for the iOS host surface. */
internal class IosApplicationRuntime {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionOwner = ReplaceableApplicationSession(
        initialSession = runBlocking { createIosApplicationSession() },
        factory = ApplicationSessionFactory { createIosApplicationSession() },
        closer = ApplicationSessionCloser(IosApplicationSession::close),
        eraser = IosLocalDataEraser(
            credentialStore = IosCredentialStores.mistralApiKey(),
            databaseName = CONVERSATION_DATABASE_NAME,
        ),
    )

    val session = sessionOwner.session
    val localDataResetPresenter = LocalDataResetPresenter(
        eraser = LocalDataEraser { sessionOwner.reset().toPresentationResult() },
        coroutineContext = Dispatchers.Default,
    )

    fun close() {
        localDataResetPresenter.close()
        closeScope.launch {
            try {
                sessionOwner.close()
            } finally {
                closeScope.cancel()
            }
        }
    }
}

private fun ApplicationSessionResetResult.toPresentationResult(): LocalDataResetResult =
    if (this == ApplicationSessionResetResult.RESET) {
        LocalDataResetResult.ERASED
    } else {
        LocalDataResetResult.FAILED
    }

internal const val CONVERSATION_DATABASE_NAME: String = "blowhard.db"
