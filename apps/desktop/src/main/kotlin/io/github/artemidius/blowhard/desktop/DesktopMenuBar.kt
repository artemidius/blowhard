@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import io.github.artemidius.blowhard.ui.presentation.ChatRole
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun FrameWindowScope.DesktopMenuBar(
    state: ConversationUiState,
    platform: DesktopPlatform,
    onAction: (ConversationAction) -> Unit,
    onExit: () -> Unit,
) {
    val latestAssistant = state.selectedConversation.messages.lastOrNull {
        it.role == ChatRole.ASSISTANT && it.text.isNotBlank()
    }
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item(
                text = "New conversation",
                onClick = { onAction(ConversationAction.NewConversation) },
                shortcut = primaryShortcut(Key.N, platform),
            )
            Separator()
            Item(text = "Exit", onClick = onExit, shortcut = primaryShortcut(Key.Q, platform))
        }
        Menu("Edit", mnemonic = 'E') {
            Item(
                text = "Copy latest response",
                onClick = { latestAssistant?.let { onAction(ConversationAction.CopyMessage(it.id)) } },
                enabled = latestAssistant != null,
                shortcut = primaryShortcut(Key.C, platform, shift = true),
            )
        }
        Menu("Conversation", mnemonic = 'C') {
            Item(
                text = "Send",
                onClick = { onAction(ConversationAction.Send) },
                enabled = state.canSend,
                shortcut = primaryShortcut(Key.Enter, platform),
            )
            Item(
                text = "Stop",
                onClick = { onAction(ConversationAction.Stop) },
                enabled = state.canStop,
                shortcut = KeyShortcut(Key.Escape),
            )
        }
    }
}

private fun primaryShortcut(key: Key, platform: DesktopPlatform, shift: Boolean = false): KeyShortcut = KeyShortcut(
    key = key,
    ctrl = platform != DesktopPlatform.MACOS,
    meta = platform == DesktopPlatform.MACOS,
    shift = shift,
)
