package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentListResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Serializes document selection and local lifecycle operations into immutable UI state. */
public class DocumentLibraryPresenter(
    private val library: DocumentLibrary,
    private val picker: DocumentPicker,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val actions = Channel<DocumentLibraryAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(DocumentLibraryUiState())

    /** Current local document metadata and sanitized feedback. */
    public val state: StateFlow<DocumentLibraryUiState> = mutableState.asStateFlow()

    init {
        scope.launch { processActions() }
        dispatch(DocumentLibraryAction.RefreshRequested)
    }

    internal fun dispatch(action: DocumentLibraryAction) {
        actions.trySend(action)
    }

    /** Stops presenter-owned work without closing application-owned storage. */
    public fun close() {
        actions.close()
        scope.cancel()
    }

    private suspend fun processActions() {
        for (action in actions) {
            try {
                handle(action)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(isBusy = false, notice = DocumentLibraryNotice.OperationUnavailable) }
            }
        }
    }

    private suspend fun handle(action: DocumentLibraryAction) {
        when (action) {
            DocumentLibraryAction.RefreshRequested -> refresh()
            DocumentLibraryAction.ImportRequested -> importSelected()
            is DocumentLibraryAction.DeleteRequested -> requestDeletion(action)
            DocumentLibraryAction.DeleteConfirmed -> deleteConfirmed()
            DocumentLibraryAction.DeleteDismissed -> mutableState.update { it.copy(pendingDeletion = null) }
            DocumentLibraryAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
        }
    }

    private suspend fun refresh() {
        mutableState.update { it.copy(isBusy = true, notice = null) }
        when (val result = library.list()) {
            is DocumentListResult.Available -> mutableState.update {
                it.copy(documents = result.documents.toList(), isBusy = false)
            }

            DocumentListResult.StorageUnavailable -> unavailable()
        }
    }

    private suspend fun importSelected() {
        mutableState.update { it.copy(isBusy = true, notice = null) }
        val source = picker.select()
        if (source == null) {
            mutableState.update { it.copy(isBusy = false) }
            return
        }
        when (val result = library.import(source)) {
            is DocumentImportResult.Imported -> imported(result)

            is DocumentImportResult.Rejected -> mutableState.update {
                it.copy(isBusy = false, notice = DocumentLibraryNotice.ImportRejected(result.reason))
            }
        }
    }

    private suspend fun imported(result: DocumentImportResult.Imported) {
        when (val listed = library.list()) {
            is DocumentListResult.Available -> mutableState.update {
                it.copy(
                    documents = listed.documents.toList(),
                    isBusy = false,
                    notice = DocumentLibraryNotice.Imported(result.document.displayName),
                )
            }

            DocumentListResult.StorageUnavailable -> unavailable()
        }
    }

    private fun requestDeletion(action: DocumentLibraryAction.DeleteRequested) {
        val document = mutableState.value.documents.firstOrNull { it.id == action.id } ?: return
        mutableState.update { it.copy(pendingDeletion = document, notice = null) }
    }

    private suspend fun deleteConfirmed() {
        val document = mutableState.value.pendingDeletion ?: return
        mutableState.update { it.copy(isBusy = true, pendingDeletion = null, notice = null) }
        when (library.delete(document.id)) {
            DocumentDeletionResult.DELETED -> mutableState.update {
                it.copy(
                    documents = it.documents.filterNot { candidate -> candidate.id == document.id },
                    isBusy = false,
                    notice = DocumentLibraryNotice.Deleted(document.displayName),
                )
            }

            DocumentDeletionResult.NOT_FOUND -> refresh()

            DocumentDeletionResult.STORAGE_UNAVAILABLE -> unavailable()
        }
    }

    private fun unavailable() {
        mutableState.update { it.copy(isBusy = false, notice = DocumentLibraryNotice.OperationUnavailable) }
    }
}
