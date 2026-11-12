package io.github.artemidius.blowhard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import io.github.artemidius.blowhard.ui.BlowhardConnectionApp

/** Android host for the shared Blowhard presentation layer. */
public class MainActivity : ComponentActivity() {
    private val applicationRuntime: AndroidApplicationRuntime by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                BlowhardConnectionApp(applicationRuntime.runtime.connectionSettingsPresenter)
            }
        }
    }
}
