@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SafeMarkdownText(text: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parseSafeMarkdown(text).forEach { block ->
                when (block) {
                    is SafeMarkdownBlock.Heading -> Heading(block)

                    is SafeMarkdownBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyLarge)

                    is SafeMarkdownBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", fontWeight = FontWeight.Bold)
                        Text(block.text, style = MaterialTheme.typography.bodyLarge)
                    }

                    is SafeMarkdownBlock.Code -> Text(
                        text = block.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Heading(block: SafeMarkdownBlock.Heading) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(block.text, style = style, fontWeight = FontWeight.SemiBold)
}
