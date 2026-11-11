package io.github.artemidius.blowhard.adapters.documents

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult

internal object PdfInspector {
    private val signature = intArrayOf('%'.code, 'P'.code, 'D'.code, 'F'.code, '-'.code)
    private val pageType = intArrayOf('/'.code, 'T'.code, 'y'.code, 'p'.code, 'e'.code)
    private val pageName = intArrayOf('/'.code, 'P'.code, 'a'.code, 'g'.code, 'e'.code)
    private val endMarker = intArrayOf('%'.code, '%'.code, 'E'.code, 'O'.code, 'F'.code)
    private val objectStreamName = intArrayOf('/'.code, 'O'.code, 'b'.code, 'j'.code, 'S'.code, 't'.code, 'm'.code)
    private val encryptionName =
        intArrayOf('/'.code, 'E'.code, 'n'.code, 'c'.code, 'r'.code, 'y'.code, 'p'.code, 't'.code)

    fun matches(content: ByteArray): Boolean = content.matchesBytes(0, signature)

    fun inspect(content: ByteArray): DocumentInspectionResult {
        val supportedStructure = hasSupportedVersion(content) &&
            hasTerminalEndMarker(content) &&
            !containsName(content, objectStreamName) &&
            !containsName(content, encryptionName)
        val pageCount = if (supportedStructure) countPageObjects(content) else 0
        return if (pageCount > 0) {
            DocumentInspectionResult.Inspected(DocumentContentDescriptor.Pdf(pageCount))
        } else {
            invalid()
        }
    }

    private fun hasSupportedVersion(content: ByteArray): Boolean = content.size >= HEADER_LENGTH &&
        content.unsignedByte(MAJOR_VERSION_OFFSET) in SUPPORTED_MAJOR_VERSIONS &&
        content.unsignedByte(VERSION_SEPARATOR_OFFSET) == '.'.code &&
        content.unsignedByte(MINOR_VERSION_OFFSET) in '0'.code..'9'.code

    private fun hasTerminalEndMarker(content: ByteArray): Boolean {
        var end = content.lastIndex
        while (end >= 0 && content.unsignedByte(end).isPdfWhitespace()) end -= 1
        val markerStart = end - endMarker.lastIndex
        return content.matchesBytes(markerStart, endMarker)
    }

    private fun countPageObjects(content: ByteArray): Int {
        var count = 0
        var offset = HEADER_LENGTH
        while (offset <= content.size - pageType.size) {
            if (!content.matchesBytes(offset, pageType)) {
                offset += 1
                continue
            }
            var valueOffset = offset + pageType.size
            while (valueOffset < content.size && content.unsignedByte(valueOffset).isPdfWhitespace()) valueOffset += 1
            if (content.matchesBytes(valueOffset, pageName)) {
                val delimiterOffset = valueOffset + pageName.size
                if (delimiterOffset == content.size ||
                    content.unsignedByte(delimiterOffset).isPdfDelimiter()
                ) {
                    count += 1
                }
            }
            offset = valueOffset.coerceAtLeast(offset + 1)
        }
        return count
    }

    private fun containsName(content: ByteArray, name: IntArray): Boolean {
        var offset = HEADER_LENGTH
        while (offset <= content.size - name.size) {
            if (content.matchesBytes(offset, name)) {
                val delimiterOffset = offset + name.size
                if (delimiterOffset == content.size ||
                    content.unsignedByte(delimiterOffset).isPdfDelimiter()
                ) {
                    return true
                }
            }
            offset += 1
        }
        return false
    }

    private fun invalid(): DocumentInspectionResult = DocumentInspectionResult.InvalidContent

    private const val HEADER_LENGTH = 8
    private const val MAJOR_VERSION_OFFSET = 5
    private const val VERSION_SEPARATOR_OFFSET = 6
    private const val MINOR_VERSION_OFFSET = 7
    private val SUPPORTED_MAJOR_VERSIONS = intArrayOf('1'.code, '2'.code)
}
