package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentDeletionResult
import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.core.api.DocumentImportResult
import io.github.artemidius.blowhard.core.api.DocumentImportSource
import io.github.artemidius.blowhard.core.api.DocumentLibrary
import io.github.artemidius.blowhard.core.api.DocumentListResult
import io.github.artemidius.blowhard.core.api.DocumentLookupResult
import io.github.artemidius.blowhard.core.api.DocumentReadResult
import io.github.artemidius.blowhard.core.api.ImportedDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentLibraryPresenterTest {
    @Test
    fun `initialization loads durable metadata`() = runTest {
        val library = FakeDocumentLibrary(documents = mutableListOf(document("one")))
        val presenter = presenter(library)

        runCurrent()

        assertEquals(listOf("one"), presenter.state.value.documents.map { it.id.value })
        assertFalse(presenter.state.value.isBusy)
        presenter.close()
    }

    @Test
    fun `successful selection imports then reloads durable ordering`() = runTest {
        val imported = document("new")
        val library = FakeDocumentLibrary(importResult = DocumentImportResult.Imported(imported))
        val source = FakeDocumentSource("meeting.pdf")
        val presenter = presenter(library, FakeDocumentPicker(source))
        runCurrent()

        presenter.dispatch(DocumentLibraryAction.ImportRequested)
        runCurrent()

        assertEquals(source, library.importedSource)
        assertEquals(listOf(imported), presenter.state.value.documents)
        assertEquals(DocumentLibraryNotice.Imported("new.pdf"), presenter.state.value.notice)
        assertFalse(presenter.state.value.toString().contains("/private/path"))
        presenter.close()
    }

    @Test
    fun `picker cancellation leaves the library unchanged`() = runTest {
        val existing = document("existing")
        val library = FakeDocumentLibrary(documents = mutableListOf(existing))
        val presenter = presenter(library, FakeDocumentPicker(null))
        runCurrent()

        presenter.dispatch(DocumentLibraryAction.ImportRequested)
        runCurrent()

        assertEquals(listOf(existing), presenter.state.value.documents)
        assertNull(library.importedSource)
        assertNull(presenter.state.value.notice)
        presenter.close()
    }

    @Test
    fun `typed import rejection is visible and dismissible`() = runTest {
        val library = FakeDocumentLibrary(
            importResult = DocumentImportResult.Rejected(DocumentImportRejection.TOO_LARGE),
        )
        val presenter = presenter(library, FakeDocumentPicker(FakeDocumentSource("large.pdf")))
        runCurrent()

        presenter.dispatch(DocumentLibraryAction.ImportRequested)
        runCurrent()
        assertEquals(
            DocumentLibraryNotice.ImportRejected(DocumentImportRejection.TOO_LARGE),
            presenter.state.value.notice,
        )

        presenter.dispatch(DocumentLibraryAction.NoticeDismissed)
        runCurrent()
        assertNull(presenter.state.value.notice)
        assertFalse(presenter.state.value.isBusy)
        presenter.close()
    }

    @Test
    fun `deletion requires the current document confirmation`() = runTest {
        val first = document("first")
        val second = document("second")
        val library = FakeDocumentLibrary(documents = mutableListOf(first, second))
        val presenter = presenter(library)
        runCurrent()

        presenter.dispatch(DocumentLibraryAction.DeleteRequested(DocumentId("missing")))
        presenter.dispatch(DocumentLibraryAction.DeleteConfirmed)
        presenter.dispatch(DocumentLibraryAction.DeleteRequested(first.id))
        presenter.dispatch(DocumentLibraryAction.DeleteDismissed)
        presenter.dispatch(DocumentLibraryAction.DeleteRequested(second.id))
        presenter.dispatch(DocumentLibraryAction.DeleteConfirmed)
        runCurrent()

        assertEquals(listOf(second.id), library.deletedIds)
        assertEquals(listOf(first), presenter.state.value.documents)
        assertEquals(DocumentLibraryNotice.Deleted("second.pdf"), presenter.state.value.notice)
        assertNull(presenter.state.value.pendingDeletion)
        presenter.close()
    }

    @Test
    fun `storage and unexpected picker failures become sanitized feedback`() = runTest {
        val unavailable = FakeDocumentLibrary(listResult = DocumentListResult.StorageUnavailable)
        val unavailablePresenter = presenter(unavailable)
        runCurrent()
        assertEquals(DocumentLibraryNotice.OperationUnavailable, unavailablePresenter.state.value.notice)
        unavailablePresenter.close()

        val failingPresenter = presenter(
            FakeDocumentLibrary(),
            FakeDocumentPicker(error = IllegalStateException("private detail")),
        )
        runCurrent()
        failingPresenter.dispatch(DocumentLibraryAction.ImportRequested)
        runCurrent()
        assertEquals(DocumentLibraryNotice.OperationUnavailable, failingPresenter.state.value.notice)
        assertFalse(failingPresenter.state.value.isBusy)
        failingPresenter.close()
    }

    @Test
    fun `missing and unavailable deletion outcomes reload or fail closed`() = runTest {
        val existing = document("existing")
        val library = FakeDocumentLibrary(
            documents = mutableListOf(existing),
            deletionResult = DocumentDeletionResult.NOT_FOUND,
        )
        val presenter = presenter(library)
        runCurrent()

        presenter.dispatch(DocumentLibraryAction.DeleteRequested(existing.id))
        presenter.dispatch(DocumentLibraryAction.DeleteConfirmed)
        runCurrent()
        assertEquals(listOf(existing), presenter.state.value.documents)
        assertTrue(library.listCalls > 1)

        library.deletionResult = DocumentDeletionResult.STORAGE_UNAVAILABLE
        presenter.dispatch(DocumentLibraryAction.DeleteRequested(existing.id))
        presenter.dispatch(DocumentLibraryAction.DeleteConfirmed)
        runCurrent()
        assertEquals(DocumentLibraryNotice.OperationUnavailable, presenter.state.value.notice)
        presenter.close()
    }

    private fun kotlinx.coroutines.test.TestScope.presenter(
        library: DocumentLibrary,
        picker: DocumentPicker = FakeDocumentPicker(null),
    ): DocumentLibraryPresenter = DocumentLibraryPresenter(
        library,
        picker,
        StandardTestDispatcher(testScheduler),
    )
}

private class FakeDocumentPicker(
    private val source: DocumentImportSource? = null,
    private val error: Exception? = null,
) : DocumentPicker {
    override suspend fun select(): DocumentImportSource? {
        error?.let { throw it }
        return source
    }
}

private class FakeDocumentLibrary(
    val documents: MutableList<ImportedDocument> = mutableListOf(),
    private val importResult: DocumentImportResult = DocumentImportResult.Rejected(
        DocumentImportRejection.INVALID_CONTENT,
    ),
    private val listResult: DocumentListResult? = null,
    var deletionResult: DocumentDeletionResult = DocumentDeletionResult.DELETED,
) : DocumentLibrary {
    var importedSource: DocumentImportSource? = null
    val deletedIds = mutableListOf<DocumentId>()
    var listCalls = 0

    override suspend fun import(source: DocumentImportSource): DocumentImportResult {
        importedSource = source
        if (importResult is DocumentImportResult.Imported) documents.add(0, importResult.document)
        return importResult
    }

    override suspend fun list(): DocumentListResult {
        listCalls += 1
        return listResult ?: DocumentListResult.Available(documents.toList())
    }

    override suspend fun find(id: DocumentId): DocumentLookupResult =
        documents.firstOrNull { it.id == id }?.let(DocumentLookupResult::Found) ?: DocumentLookupResult.NotFound

    override suspend fun delete(id: DocumentId): DocumentDeletionResult {
        deletedIds += id
        if (deletionResult == DocumentDeletionResult.DELETED) documents.removeAll { it.id == id }
        return deletionResult
    }
}

private data class FakeDocumentSource(override val displayName: String) : DocumentImportSource {
    override val declaredSizeBytes: Long = 1

    override suspend fun read(maximumBytes: Long): DocumentReadResult = DocumentReadResult.Content(byteArrayOf(1))
}

private fun document(id: String): ImportedDocument = ImportedDocument(
    id = DocumentId(id),
    displayName = "$id.pdf",
    sizeBytes = 1,
    importedAtEpochMilliseconds = 1,
    content = DocumentContentDescriptor.Pdf(1),
)
