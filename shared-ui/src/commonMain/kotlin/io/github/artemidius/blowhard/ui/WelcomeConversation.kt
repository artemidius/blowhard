@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.ui.presentation.ConversationAction

@Composable
internal fun WelcomeConversation(modifier: Modifier, onAction: (ConversationAction) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val compact = maxWidth < 520.dp
        val shortWindow = maxHeight < 480.dp
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (shortWindow) 12.dp else 20.dp),
        ) {
            OrbitalEmblem(compact || shortWindow)
            Text(
                "YOUR NEXT BIG IDEA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Make room for\nsomething brilliant.",
                textAlign = TextAlign.Center,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            )
            Text(
                "Think it through. Build it out. Make it yours.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StarterCard("Explore an idea", "Help me think through a new idea.", onAction)
                StarterCard("Solve a problem", "Help me break down a difficult problem step by step.", onAction)
                StarterCard("Refine my writing", "Help me improve the clarity of this text:\n", onAction)
            }
        }
    }
}

@Composable
private fun StarterCard(label: String, prompt: String, onAction: (ConversationAction) -> Unit) {
    Surface(
        onClick = { onAction(ConversationAction.ComposerChanged(prompt)) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text("$label  ↗", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
    }
}
