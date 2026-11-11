package io.github.artemidius.blowhard.adapters.fixture

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.domain.DocumentOcrPage
import io.github.artemidius.blowhard.core.domain.DocumentOcrPort
import io.github.artemidius.blowhard.core.domain.DocumentOcrPortResult
import io.github.artemidius.blowhard.core.domain.DocumentOcrRequest

/** Deterministic offline OCR adapter for tests, screenshots, and release evidence. */
public class FixtureDocumentOcrPort : DocumentOcrPort {
    override suspend fun process(request: DocumentOcrRequest): DocumentOcrPortResult {
        val pageCount = when (val content = request.document.content) {
            is DocumentContentDescriptor.Pdf -> content.pageCount
            is DocumentContentDescriptor.Image -> 1
        }
        return DocumentOcrPortResult.Processed(
            processor = PROCESSOR,
            pages = List(pageCount) { index -> DocumentOcrPage(index, markdown(request, index, pageCount)) },
        )
    }

    private fun markdown(request: DocumentOcrRequest, index: Int, pageCount: Int): String = """
        # ${request.document.displayName}

        Deterministic OCR fixture for page ${index + 1} of $pageCount.

        | Field | Value |
        | --- | --- |
        | Source | Local import |
        | Page | ${index + 1} |
    """.trimIndent()

    private companion object {
        const val PROCESSOR = "fixture-ocr-v1"
    }
}
