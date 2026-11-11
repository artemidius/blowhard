package io.github.artemidius.blowhard.ui

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.api.DocumentImportRejection
import io.github.artemidius.blowhard.ui.presentation.DocumentLibraryNotice

internal fun DocumentContentDescriptor.description(): String = when (this) {
    is DocumentContentDescriptor.Pdf -> "PDF · $pageCount ${if (pageCount == 1) "page" else "pages"}"
    is DocumentContentDescriptor.Image -> "${format.label()} · $widthPixels × $heightPixels px"
}

private fun DocumentFormat.label(): String = when (this) {
    DocumentFormat.PDF -> "PDF"
    DocumentFormat.PNG -> "PNG"
    DocumentFormat.JPEG -> "JPEG"
}

internal fun Long.readableByteCount(): String = when {
    this >= BYTES_PER_MEGABYTE -> "${this / BYTES_PER_MEGABYTE} MB"
    this >= BYTES_PER_KILOBYTE -> "${this / BYTES_PER_KILOBYTE} KB"
    else -> "$this B"
}

internal fun DocumentLibraryNotice.message(): String = when (this) {
    is DocumentLibraryNotice.Imported -> "Imported “$displayName” into local app storage."
    is DocumentLibraryNotice.Deleted -> "Deleted “$displayName” from local app storage."
    is DocumentLibraryNotice.ImportRejected -> reason.message()
    DocumentLibraryNotice.OperationUnavailable -> "The local document operation could not complete safely."
}

private fun DocumentImportRejection.message(): String = when (this) {
    DocumentImportRejection.INVALID_NAME -> "The selected document has an invalid display name."
    DocumentImportRejection.EMPTY_CONTENT -> "The selected document is empty."
    DocumentImportRejection.TOO_LARGE -> "The selected document exceeds the 20 MB local import limit."
    DocumentImportRejection.UNSUPPORTED_FORMAT -> "Choose a structurally valid PDF, PNG, or JPEG."
    DocumentImportRejection.INVALID_CONTENT -> "The selected document is incomplete or malformed."
    DocumentImportRejection.PAGE_LIMIT_EXCEEDED -> "The selected PDF exceeds the 100-page import limit."
    DocumentImportRejection.PIXEL_LIMIT_EXCEEDED -> "The selected image exceeds the 40-megapixel import limit."
    DocumentImportRejection.SOURCE_UNAVAILABLE -> "The selected document could not be read safely."
    DocumentImportRejection.STORAGE_UNAVAILABLE -> "Local document storage is unavailable."
}

private const val BYTES_PER_KILOBYTE = 1024L
private const val BYTES_PER_MEGABYTE = 1024L * BYTES_PER_KILOBYTE
