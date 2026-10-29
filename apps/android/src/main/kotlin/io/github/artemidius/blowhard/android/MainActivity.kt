package io.github.artemidius.blowhard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.artemidius.blowhard.ui.BlowhardMobileApp

/** Android host for the shared Blowhard presentation layer. */
public class MainActivity : ComponentActivity() {
    private val applicationRuntime: AndroidApplicationRuntime by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val runtime = applicationRuntime.runtime
            BlowhardMobileApp(
                conversationPresenter = runtime.conversationPresenter,
                connectionSettingsPresenter = runtime.connectionSettingsPresenter,
                runInspectorPresenter = runtime.runInspectorPresenter,
                documentLibraryPresenter = runtime.documentLibraryPresenter,
                meetingNotePresenter = runtime.meetingNotePresenter,
            )
        }
    }
}
