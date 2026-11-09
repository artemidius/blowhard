package io.github.artemidius.blowhard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class ConversationLayout {
    COMPACT,
    WIDE,
}

internal fun conversationLayoutFor(width: Dp): ConversationLayout =
    if (width >= WIDE_LAYOUT_THRESHOLD) ConversationLayout.WIDE else ConversationLayout.COMPACT

private val WIDE_LAYOUT_THRESHOLD = 760.dp
