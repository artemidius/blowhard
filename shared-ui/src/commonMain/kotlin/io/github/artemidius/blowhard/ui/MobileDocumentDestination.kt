@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package io.github.artemidius.blowhard.ui

import androidx.compose.runtime.Composable
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteAction
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter

@Composable
internal fun MobileDocumentDestination(
    navigation: MobileNavigationState,
    documentLibraryState: DocumentLibraryUiState,
    documentLibraryPresenter: DocumentLibraryPresenter,
    meetingNotePresenter: MeetingNotePresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    DocumentLibraryScreen(
        documentLibraryState,
        documentLibraryPresenter::dispatch,
        onGenerateMeetingNote = { sourceId ->
            meetingNotePresenter.dispatch(MeetingNoteAction.Generate(sourceId))
            navigate(navigation.open(MobileDestination.MEETING_NOTE))
        },
        onBack = { navigate(navigation.selectRoot(MobileRootDestination.CHATS)) },
    )
}
