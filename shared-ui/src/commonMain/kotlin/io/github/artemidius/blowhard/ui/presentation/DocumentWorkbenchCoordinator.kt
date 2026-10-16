package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentExtractionLookupResult
import io.github.artemidius.blowhard.core.api.DocumentOcr
import io.github.artemidius.blowhard.core.api.DocumentProcessingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class DocumentWorkbenchCoordinator(
    private val ocr: DocumentOcr,
    private val state: MutableStateFlow<DocumentLibraryUiState>,
) {
    suspend fun open(action: DocumentLibraryAction.OpenRequested): Boolean {
        val document = state.value.documents.firstOrNull { it.id == action.id } ?: return false
        state.update {
            it.copy(selectedDocument = document, extraction = null, selectedPageIndex = 0, isBusy = true, notice = null)
        }
        return when (val result = ocr.find(document.id)) {
            is DocumentExtractionLookupResult.Found -> {
                state.update { it.copy(extraction = result.extraction, selectedPageIndex = 0, isBusy = false) }
                false
            }

            DocumentExtractionLookupResult.NotProcessed -> {
                state.update { it.copy(isBusy = false) }
                false
            }

            DocumentExtractionLookupResult.DocumentNotFound -> {
                state.update { it.copy(selectedDocument = null, extraction = null, isBusy = false) }
                true
            }

            DocumentExtractionLookupResult.StorageUnavailable -> {
                unavailable()
                false
            }
        }
    }

    fun close() {
        state.update {
            it.copy(selectedDocument = null, extraction = null, selectedPageIndex = 0, notice = null)
        }
    }

    suspend fun process(action: DocumentLibraryAction.ProcessRequested) {
        val document = state.value.documents.firstOrNull { it.id == action.id } ?: return
        state.update { it.copy(selectedDocument = document, isBusy = true, notice = null) }
        when (val result = ocr.process(document.id)) {
            is DocumentProcessingResult.Processed -> state.update {
                it.copy(
                    extraction = result.extraction,
                    selectedPageIndex = 0,
                    isBusy = false,
                    notice = DocumentLibraryNotice.Processed(document.displayName),
                )
            }

            is DocumentProcessingResult.Rejected -> state.update {
                it.copy(isBusy = false, notice = DocumentLibraryNotice.ProcessingRejected(result.reason))
            }
        }
    }

    fun selectPage(action: DocumentLibraryAction.PageSelected) {
        val pages = state.value.extraction?.pages ?: return
        if (action.index !in pages.indices) return
        state.update { it.copy(selectedPageIndex = action.index) }
    }

    private fun unavailable() {
        state.update { it.copy(isBusy = false, notice = DocumentLibraryNotice.OperationUnavailable) }
    }
}
