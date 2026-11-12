@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.artemidius.blowhard.ui.presentation.ConnectionSettingsPresenter
import io.github.artemidius.blowhard.ui.presentation.ConversationPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter
import io.github.artemidius.blowhard.ui.presentation.RunInspectorPresenter

/** Mobile-first root shared by the Android and iOS application hosts. */
@Composable
public fun BlowhardMobileApp(
    conversationPresenter: ConversationPresenter,
    connectionSettingsPresenter: ConnectionSettingsPresenter,
    runInspectorPresenter: RunInspectorPresenter,
    documentLibraryPresenter: DocumentLibraryPresenter,
    meetingNotePresenter: MeetingNotePresenter,
) {
    val conversationState by conversationPresenter.state.collectAsState()
    val settingsState by connectionSettingsPresenter.state.collectAsState()
    val inspectorState by runInspectorPresenter.state.collectAsState()
    val documentLibraryState by documentLibraryPresenter.state.collectAsState()
    val meetingNoteState by meetingNotePresenter.state.collectAsState()
    var navigation by rememberSaveable(stateSaver = MobileNavigationStateSaver) {
        mutableStateOf(MobileNavigationState.initial())
    }

    MaterialTheme(colorScheme = BlowhardColors, shapes = BlowhardShapes, typography = BlowhardTypography) {
        Surface {
            MobileWorkspace(
                navigation = navigation,
                conversationState = conversationState,
                settingsState = settingsState,
                inspectorState = inspectorState,
                documentLibraryState = documentLibraryState,
                meetingNoteState = meetingNoteState,
                conversationPresenter = conversationPresenter,
                connectionSettingsPresenter = connectionSettingsPresenter,
                runInspectorPresenter = runInspectorPresenter,
                documentLibraryPresenter = documentLibraryPresenter,
                meetingNotePresenter = meetingNotePresenter,
                navigate = { navigation = it },
            )
        }
    }
}

private val MobileNavigationStateSaver = listSaver(
    save = { navigation -> navigation.savedRoutes() },
    restore = { routes -> MobileNavigationState.restore(routes) },
)
