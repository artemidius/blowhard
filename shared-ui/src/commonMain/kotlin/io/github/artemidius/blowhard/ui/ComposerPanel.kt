@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.artemidius.blowhard.core.api.ConnectionSettingsState
import io.github.artemidius.blowhard.core.api.GenerationMode
import io.github.artemidius.blowhard.core.api.ImportedDocument
import io.github.artemidius.blowhard.ui.presentation.ConversationAction
import io.github.artemidius.blowhard.ui.presentation.ConversationUiState
import io.github.artemidius.blowhard.ui.presentation.DemoScenario

@Composable
internal fun ComposerPanel(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    availableImages: List<ImportedDocument>,
    onOpenDocuments: () -> Unit,
    onAction: (ConversationAction) -> Unit,
    modifier: Modifier = Modifier,
    mobile: Boolean = false,
) {
    val composerFocus = remember { FocusRequester() }
    val inputInteraction = remember { MutableInteractionSource() }
    val inputFocused by inputInteraction.collectIsFocusedAsState()
    LaunchedEffect(state.selectedConversationId, state.selectedConversation.runState.isActive) {
        if (!mobile && !state.selectedConversation.runState.isActive) composerFocus.requestFocus()
    }
    Column(
        modifier = modifier
            .widthIn(max = 920.dp)
            .fillMaxWidth()
            .padding(horizontal = if (mobile) 12.dp else 24.dp, vertical = if (mobile) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                if (inputFocused) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ComposerInput(state, connection, composerFocus, inputInteraction, onAction)
                ImageAttachmentSelector(state, connection, availableImages, onOpenDocuments, onAction)
                ComposerActions(state = state, onAction = onAction, showKeyboardShortcut = !mobile)
            }
        }
        if (connection.mode == GenerationMode.DEMO) ScenarioSelector(state = state, onAction = onAction)
    }
}

@Composable
private fun ImageAttachmentSelector(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    availableImages: List<ImportedDocument>,
    onOpenDocuments: () -> Unit,
    onAction: (ConversationAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modelSupportsVision = connection.models
        .firstOrNull { it.id == connection.selectedModelId }
        ?.supportsVision == true
    val enabled = !state.selectedConversation.runState.isActive
    when {
        state.selectedImage != null -> SelectedImageRow(state, enabled, onAction)

        !modelSupportsVision -> Unit

        availableImages.isEmpty() -> TextButton(onClick = onOpenDocuments, enabled = enabled) {
            Text("+ Import an image")
        }

        else -> ImageChoices(availableImages, expanded, enabled, { expanded = it }, onAction)
    }
}

@Composable
private fun ImageChoices(
    availableImages: List<ImportedDocument>,
    expanded: Boolean,
    enabled: Boolean,
    setExpanded: (Boolean) -> Unit,
    onAction: (ConversationAction) -> Unit,
) {
    TextButton(onClick = { setExpanded(!expanded) }, enabled = enabled) {
        Text(if (expanded) "Close image picker" else "+ Attach local image")
    }
    if (!expanded) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(availableImages, key = { it.id.value }) { image ->
            FilterChip(
                selected = false,
                onClick = {
                    onAction(ConversationAction.ImageSelected(image.id, image.displayName))
                    setExpanded(false)
                },
                label = { Text(image.displayName, maxLines = 1) },
            )
        }
    }
    Text(
        "Only the image you select is sent with the next prompt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SelectedImageRow(state: ConversationUiState, enabled: Boolean, onAction: (ConversationAction) -> Unit) {
    val image = requireNotNull(state.selectedImage)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Image · ${image.displayName}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        TextButton(onClick = { onAction(ConversationAction.ImageCleared) }, enabled = enabled) {
            Text("Remove")
        }
    }
}

@Composable
private fun ComposerInput(
    state: ConversationUiState,
    connection: ConnectionSettingsState,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    onAction: (ConversationAction) -> Unit,
) {
    OutlinedTextField(
        value = state.composer,
        onValueChange = { onAction(ConversationAction.ComposerChanged(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val sendShortcut = event.type == KeyEventType.KeyDown &&
                    event.key == Key.Enter &&
                    (event.isCtrlPressed || event.isMetaPressed)
                if (sendShortcut && state.canSend) {
                    onAction(ConversationAction.Send)
                    true
                } else {
                    false
                }
            },
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        ),
        placeholder = { Text(connection.composerPlaceholder()) },
        minLines = 2,
        maxLines = 6,
        enabled = !state.selectedConversation.runState.isActive,
    )
}

private fun ConnectionSettingsState.composerPlaceholder(): String = if (mode == GenerationMode.DEMO) {
    "Try a message in the offline demo…"
} else {
    "Ask anything. Start something."
}

@Composable
private fun ScenarioSelector(state: ConversationUiState, onAction: (ConversationAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(
            "Demo scenario · ${state.scenario.label}  ${if (expanded) "−" else "+"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!expanded) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DemoScenario.entries) { scenario ->
            FilterChip(
                selected = state.scenario == scenario,
                onClick = { onAction(ConversationAction.ScenarioChanged(scenario)) },
                label = { Text(scenario.label) },
            )
        }
    }
    Text(
        state.scenario.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ComposerActions(
    state: ConversationUiState,
    onAction: (ConversationAction) -> Unit,
    showKeyboardShortcut: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (state.selectedConversation.runState.isActive) {
                state.selectedConversation.runState.displayLabel()
            } else if (showKeyboardShortcut) {
                "⌘ / Ctrl + Enter to send"
            } else {
                "Ready"
            },
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (state.canStop) {
            OutlinedButton(onClick = { onAction(ConversationAction.Stop) }) { Text("Stop") }
        } else {
            Button(onClick = {
                onAction(ConversationAction.Send)
            }, enabled = state.canSend, shape = MaterialTheme.shapes.medium) {
                Text("Send ↑")
            }
        }
    }
}
