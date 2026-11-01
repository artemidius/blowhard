package io.github.artemidius.blowhard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.artemidius.blowhard.ui.BlowhardMobileApp

/** Android host for the shared Blowhard presentation layer. */
public class MainActivity : ComponentActivity() {
    private val applicationRuntime: AndroidApplicationRuntime by viewModels()
    private val documentSelection = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        applicationRuntime.documentPicker.complete(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationRuntime.documentPicker.bind(documentSelection)
        enableEdgeToEdge()
        setContent {
            val session by applicationRuntime.session.collectAsState()
            val runtime = session.runtime
            BlowhardMobileApp(
                conversationPresenter = runtime.conversationPresenter,
                connectionSettingsPresenter = runtime.connectionSettingsPresenter,
                runInspectorPresenter = runtime.runInspectorPresenter,
                documentLibraryPresenter = runtime.documentLibraryPresenter,
                meetingNotePresenter = runtime.meetingNotePresenter,
                localDataResetPresenter = applicationRuntime.localDataResetPresenter,
            )
        }
    }

    override fun onDestroy() {
        applicationRuntime.documentPicker.unbind(documentSelection)
        super.onDestroy()
    }
}
