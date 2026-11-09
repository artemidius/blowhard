package io.github.artemidius.blowhard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter

/** Root presentation entry point shared by Blowhard clients. */
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
public fun BlowhardApp(presenter: ConversationPresenter) {
    val state by presenter.state.collectAsState()
    MaterialTheme(colorScheme = BlowhardColors) {
        ConversationScreen(state = state, onAction = presenter::dispatch)
    }
}

private val BlowhardColors = lightColorScheme(
    primary = Color(0xFF315C72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3ECF8),
    onPrimaryContainer = Color(0xFF102F3E),
    secondary = Color(0xFF7A4F01),
    secondaryContainer = Color(0xFFFFDEA4),
    surface = Color(0xFFFAFCFD),
    surfaceVariant = Color(0xFFE7EEF1),
    background = Color(0xFFF3F7F8),
)
