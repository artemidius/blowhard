package io.github.artemidius.blowhard.ui.presentation

internal class MessageClipboard(private val writer: ClipboardWriter) {
    fun copy(state: ConversationUiState, messageId: String): ConversationUiState {
        val text = state.selectedConversation.messages.firstOrNull { it.id == messageId }?.text.orEmpty()
        return if (text.isBlank()) {
            state
        } else {
            val result = try {
                writer.write(text)
            } catch (_: Exception) {
                ClipboardWriteResult.UNAVAILABLE
            }
            val notice = when (result) {
                ClipboardWriteResult.COPIED -> UiNotice("Message copied to the clipboard.")
                ClipboardWriteResult.UNAVAILABLE -> UiNotice("The clipboard is unavailable in this desktop session.")
            }
            state.withSelectedNotice(notice)
        }
    }
}
