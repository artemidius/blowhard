@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuBarScope
import io.github.artemidius.blowhard.ui.presentation.ChatRole
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState

@Composable
internal fun FrameWindowScope.DesktopMenuBar(
    state: ConversationUiState,
    platform: DesktopPlatform,
    onAction: (ConversationAction) -> Unit,
    onExit: () -> Unit,
    conversationEnabled: Boolean = true,
) {
    val latestAssistant = state.selectedConversation.messages.lastOrNull {
        it.role == ChatRole.ASSISTANT && it.text.isNotBlank()
    }
    val latestRegenerable = state.selectedConversation.messages.lastOrNull {
        it.role == ChatRole.ASSISTANT && it.canRegenerate
    }
    MenuBar {
        fileMenu(platform, onAction, onExit, conversationEnabled)
        editMenu(platform, latestAssistant?.id, onAction, conversationEnabled)
        conversationMenu(state, platform, latestRegenerable?.id, onAction, conversationEnabled)
    }
}

@Composable
private fun MenuBarScope.fileMenu(
    platform: DesktopPlatform,
    onAction: (ConversationAction) -> Unit,
    onExit: () -> Unit,
    conversationEnabled: Boolean,
) {
    Menu("File", mnemonic = 'F') {
        Item(
            text = "New conversation",
            onClick = { onAction(ConversationAction.NewConversation) },
            enabled = conversationEnabled,
            shortcut = primaryShortcut(Key.N, platform),
        )
        Separator()
        Item(text = "Exit", onClick = onExit, shortcut = primaryShortcut(Key.Q, platform))
    }
}

@Composable
private fun MenuBarScope.editMenu(
    platform: DesktopPlatform,
    latestAssistantId: String?,
    onAction: (ConversationAction) -> Unit,
    conversationEnabled: Boolean,
) {
    Menu("Edit", mnemonic = 'E') {
        Item(
            text = "Copy latest response",
            onClick = { latestAssistantId?.let { onAction(ConversationAction.CopyMessage(it)) } },
            enabled = conversationEnabled && latestAssistantId != null,
            shortcut = primaryShortcut(Key.C, platform, shift = true),
        )
    }
}

@Composable
private fun MenuBarScope.conversationMenu(
    state: ConversationUiState,
    platform: DesktopPlatform,
    latestRegenerableId: String?,
    onAction: (ConversationAction) -> Unit,
    conversationEnabled: Boolean,
) {
    Menu("Conversation", mnemonic = 'C') {
        Item(
            text = "Send",
            onClick = { onAction(ConversationAction.Send) },
            enabled = conversationEnabled && state.canSend,
            shortcut = primaryShortcut(Key.Enter, platform),
        )
        Item(
            text = "Stop",
            onClick = { onAction(ConversationAction.Stop) },
            enabled = conversationEnabled && state.canStop,
            shortcut = KeyShortcut(Key.Escape),
        )
        Item(
            text = "Regenerate latest response",
            onClick = { latestRegenerableId?.let { onAction(ConversationAction.Regenerate(it)) } },
            enabled =
            conversationEnabled && latestRegenerableId != null && !state.selectedConversation.runState.isActive,
            shortcut = primaryShortcut(Key.R, platform, shift = true),
        )
    }
}

private fun primaryShortcut(key: Key, platform: DesktopPlatform, shift: Boolean = false): KeyShortcut = KeyShortcut(
    key = key,
    ctrl = platform != DesktopPlatform.MACOS,
    meta = platform == DesktopPlatform.MACOS,
    shift = shift,
)
