package io.github.artemidius.blowhard.adapters.documents

import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult
import io.github.artemidius.blowhard.core.domain.DocumentInspector

/**
 * Performs bounded, dependency-free structural inspection of supported document bytes.
 *
 * The inspector trusts neither filenames nor media types. It does not decode pixels, execute PDF
 * content, or access external resources.
 */
public object PortableDocumentInspector : DocumentInspector {
    override suspend fun inspect(content: ByteArray): DocumentInspectionResult = when {
        PngInspector.matches(content) -> PngInspector.inspect(content)
        JpegInspector.matches(content) -> JpegInspector.inspect(content)
        PdfInspector.matches(content) -> PdfInspector.inspect(content)
        else -> DocumentInspectionResult.UnsupportedFormat
    }
}
