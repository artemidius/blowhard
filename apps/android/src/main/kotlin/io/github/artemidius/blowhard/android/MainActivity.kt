package io.github.artemidius.blowhard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import io.github.artemidius.blowhard.ui.BlowhardConnectionApp
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import kotlinx.coroutines.Dispatchers

/** Android host for the shared Blowhard presentation layer. */
public class MainActivity : ComponentActivity() {
    private lateinit var presenter: ConnectionSettingsPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        presenter = ConnectionSettingsPresenter(
            connectionSettings = UnavailableAndroidConnectionSettings(),
            coroutineContext = Dispatchers.Default,
        )
        setContent {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                BlowhardConnectionApp(presenter)
            }
        }
    }

    override fun onDestroy() {
        presenter.close()
        super.onDestroy()
    }
}
