package io.github.artemidius.blowhard.adapters.documents

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult

internal object JpegInspector {
    fun matches(content: ByteArray): Boolean = content.size >= MARKER_BYTES &&
        content.unsignedByte(0) == MARKER_PREFIX && content.unsignedByte(1) == START_OF_IMAGE

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun inspect(content: ByteArray): DocumentInspectionResult {
        if (content.size < MINIMUM_SIZE || !endsWithEndMarker(content)) return invalid()
        var offset = MARKER_BYTES
        var dimensions: Pair<Int, Int>? = null

        while (offset < content.size - MARKER_BYTES) {
            if (content.unsignedByte(offset) != MARKER_PREFIX) return invalid()
            while (offset < content.size && content.unsignedByte(offset) == MARKER_PREFIX) offset += 1
            if (offset >= content.size) return invalid()

            val marker = content.unsignedByte(offset)
            offset += 1
            if (marker == END_OF_IMAGE || marker == 0) return invalid()
            if (marker == TEMPORARY || marker in RESTART_MARKER_RANGE) continue
            if (offset + LENGTH_BYTES > content.size) return invalid()

            val segmentLength = content.readUnsignedShort(offset)
            if (segmentLength < LENGTH_BYTES || offset + segmentLength > content.size) return invalid()
            if (marker == START_OF_SCAN) {
                val (width, height) = dimensions ?: return invalid()
                if (!hasValidScanHeader(content, offset, segmentLength)) return invalid()
                return DocumentInspectionResult.Inspected(
                    DocumentContentDescriptor.Image(DocumentFormat.JPEG, width, height),
                )
            }
            if (marker in START_OF_FRAME_MARKERS) {
                if (dimensions != null) return invalid()
                dimensions = readFrame(content, offset, segmentLength) ?: return invalid()
            }
            offset += segmentLength
        }
        return invalid()
    }

    private fun readFrame(content: ByteArray, offset: Int, segmentLength: Int): Pair<Int, Int>? {
        if (segmentLength < FRAME_HEADER_LENGTH) return null
        val height = content.readUnsignedShort(offset + HEIGHT_OFFSET)
        val width = content.readUnsignedShort(offset + WIDTH_OFFSET)
        val components = content.unsignedByte(offset + COMPONENTS_OFFSET)
        val expectedLength = FRAME_HEADER_LENGTH + COMPONENT_BYTES * components
        val invalidGeometry = width == 0 || height == 0 || components == 0
        return if (invalidGeometry || segmentLength < expectedLength) {
            null
        } else {
            width to height
        }
    }

    private fun hasValidScanHeader(content: ByteArray, offset: Int, segmentLength: Int): Boolean {
        if (segmentLength < SCAN_HEADER_LENGTH) return false
        val components = content.unsignedByte(offset + SCAN_COMPONENTS_OFFSET)
        return components > 0 && segmentLength >= SCAN_HEADER_LENGTH + SCAN_COMPONENT_BYTES * components
    }

    private fun endsWithEndMarker(content: ByteArray): Boolean =
        content.unsignedByte(content.lastIndex - 1) == MARKER_PREFIX &&
            content.unsignedByte(content.lastIndex) == END_OF_IMAGE

    private fun invalid(): DocumentInspectionResult = DocumentInspectionResult.InvalidContent

    private const val MARKER_PREFIX = 0xff
    private const val START_OF_IMAGE = 0xd8
    private const val END_OF_IMAGE = 0xd9
    private const val START_OF_SCAN = 0xda
    private const val TEMPORARY = 0x01
    private val RESTART_MARKER_RANGE = 0xd0..0xd7
    private val START_OF_FRAME_MARKERS =
        setOf(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf)
    private const val MARKER_BYTES = 2
    private const val LENGTH_BYTES = 2
    private const val MINIMUM_SIZE = 12
    private const val FRAME_HEADER_LENGTH = 8
    private const val COMPONENT_BYTES = 3
    private const val SCAN_HEADER_LENGTH = 6
    private const val SCAN_COMPONENT_BYTES = 2
    private const val SCAN_COMPONENTS_OFFSET = 2
    private const val HEIGHT_OFFSET = 3
    private const val WIDTH_OFFSET = 5
    private const val COMPONENTS_OFFSET = 7
}
