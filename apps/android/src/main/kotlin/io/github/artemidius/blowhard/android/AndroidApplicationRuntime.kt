package io.github.artemidius.blowhard.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.artemidius.blowhard.adapters.mobile.AndroidCredentialStores
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

/** Retains and safely replaces application-owned services across Android activity recreation. */
internal class AndroidApplicationRuntime(application: Application) : AndroidViewModel(application) {
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val applicationContext = application.applicationContext
    val documentPicker = AndroidDocumentPicker(applicationContext.contentResolver)
    private val sessionOwner = ReplaceableApplicationSession(
        initialSession = runBlocking { createAndroidApplicationSession(applicationContext, documentPicker) },
        factory = ApplicationSessionFactory { createAndroidApplicationSession(applicationContext, documentPicker) },
        closer = ApplicationSessionCloser(AndroidApplicationSession::close),
        eraser = AndroidLocalDataEraser(
            context = applicationContext,
            credentialStore = AndroidCredentialStores.mistralApiKey(applicationContext),
            databaseName = CONVERSATION_DATABASE_NAME,
        ),
    )

    val session = sessionOwner.session
    val localDataResetPresenter = LocalDataResetPresenter(
        eraser = LocalDataEraser { sessionOwner.reset().toPresentationResult() },
        coroutineContext = Dispatchers.Default,
    )

    override fun onCleared() {
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
