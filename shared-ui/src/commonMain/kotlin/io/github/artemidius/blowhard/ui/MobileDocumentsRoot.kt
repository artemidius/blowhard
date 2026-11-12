@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package io.github.artemidius.blowhard.ui

import androidx.compose.runtime.Composable
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryPresenter
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryUiState
import io.github.artemidius.blowhard.ui.presentation.MeetingNotePresenter
import io.github.artemidius.blowhard.ui.presentation.MeetingNoteUiState

@Composable
internal fun MobileDocumentsRoot(
    navigation: MobileNavigationState,
    documentLibraryState: DocumentLibraryUiState,
    meetingNoteState: MeetingNoteUiState,
    documentLibraryPresenter: DocumentLibraryPresenter,
    meetingNotePresenter: MeetingNotePresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    when (navigation.current) {
        MobileDestination.DOCUMENTS -> MobileDocumentDestination(
            navigation,
            documentLibraryState,
            documentLibraryPresenter,
            meetingNotePresenter,
            navigate,
        )

        MobileDestination.MEETING_NOTE -> MobileMeetingNoteDestination(
            navigation,
            meetingNoteState,
            meetingNotePresenter,
            navigate,
        )

        else -> error("Unexpected destination for the Documents root")
    }
}

@Composable
private fun MobileMeetingNoteDestination(
    navigation: MobileNavigationState,
    state: MeetingNoteUiState,
    presenter: MeetingNotePresenter,
    navigate: (MobileNavigationState) -> Unit,
) {
    MeetingNoteScreen(state, presenter::dispatch, onBack = { navigate(navigation.back()) })
}
