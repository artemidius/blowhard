package io.github.artemidius.blowhard.adapters.documents

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor
import io.github.artemidius.blowhard.core.api.DocumentFormat
import io.github.artemidius.blowhard.core.domain.DocumentInspectionResult

internal object PngInspector {
    private val signature = intArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    fun matches(content: ByteArray): Boolean = content.matchesBytes(0, signature)

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun inspect(content: ByteArray): DocumentInspectionResult {
        var offset = signature.size
        var dimensions: Pair<Int, Int>? = null
        var sawImageData = false

        while (offset <= content.size - CHUNK_OVERHEAD) {
            val length = content.readUnsignedInt(offset)
            if (length > Int.MAX_VALUE) return invalid()
            val dataLength = length.toInt()
            val typeOffset = offset + LENGTH_BYTES
            val dataOffset = typeOffset + TYPE_BYTES
            val crcOffset = dataOffset.toLong() + dataLength
            val chunkEnd = crcOffset + CRC_BYTES
            if (chunkEnd > content.size) return invalid()

            val type = content.readUnsignedInt(typeOffset)
            if (content.readUnsignedInt(crcOffset.toInt()) != crc32(content, typeOffset, dataLength + TYPE_BYTES)) {
                return invalid()
            }

            when (type) {
                IHDR -> {
                    if (offset != signature.size || dimensions != null || dataLength != IHDR_LENGTH) return invalid()
                    dimensions = readDimensions(content, dataOffset) ?: return invalid()
                }

                IDAT -> {
                    if (dimensions == null || dataLength == 0) return invalid()
                    sawImageData = true
                }

                IEND -> {
                    if (dataLength != 0 || chunkEnd.toInt() != content.size || !sawImageData) return invalid()
                    val (width, height) = dimensions ?: return invalid()
                    return DocumentInspectionResult.Inspected(
                        DocumentContentDescriptor.Image(DocumentFormat.PNG, width, height),
                    )
                }
            }
            offset = chunkEnd.toInt()
        }
        return invalid()
    }

    private fun readDimensions(content: ByteArray, offset: Int): Pair<Int, Int>? {
        val width = content.readUnsignedInt(offset)
        val height = content.readUnsignedInt(offset + INT_BYTES)
        if (width !in 1..Int.MAX_VALUE.toLong() || height !in 1..Int.MAX_VALUE.toLong()) return null

        val bitDepth = content.unsignedByte(offset + BIT_DEPTH_OFFSET)
        val colourType = content.unsignedByte(offset + COLOUR_TYPE_OFFSET)
        val validDepth = when (colourType) {
            GREYSCALE -> bitDepth in intArrayOf(1, 2, 4, 8, 16)
            TRUECOLOUR, GREYSCALE_ALPHA, TRUECOLOUR_ALPHA -> bitDepth == 8 || bitDepth == 16
            INDEXED_COLOUR -> bitDepth in intArrayOf(1, 2, 4, 8)
            else -> false
        }
        val methodsAreSupported = content.unsignedByte(offset + COMPRESSION_OFFSET) == 0 &&
            content.unsignedByte(offset + FILTER_OFFSET) == 0 &&
            content.unsignedByte(offset + INTERLACE_OFFSET) in 0..1
        return if (validDepth && methodsAreSupported) width.toInt() to height.toInt() else null
    }

    private fun crc32(content: ByteArray, offset: Int, length: Int): Long {
        var crc = UInt.MAX_VALUE
        repeat(length) { index ->
            crc = crc xor content.unsignedByte(offset + index).toUInt()
            repeat(Byte.SIZE_BITS) {
                crc = if ((crc and 1u) == 1u) (crc shr 1) xor CRC_POLYNOMIAL else crc shr 1
            }
        }
        return (crc xor UInt.MAX_VALUE).toLong()
    }

    private fun invalid(): DocumentInspectionResult = DocumentInspectionResult.InvalidContent

    private const val LENGTH_BYTES = 4
    private const val TYPE_BYTES = 4
    private const val CRC_BYTES = 4
    private const val CHUNK_OVERHEAD = LENGTH_BYTES + TYPE_BYTES + CRC_BYTES
    private const val INT_BYTES = 4
    private const val IHDR_LENGTH = 13
    private const val BIT_DEPTH_OFFSET = 8
    private const val COLOUR_TYPE_OFFSET = 9
    private const val COMPRESSION_OFFSET = 10
    private const val FILTER_OFFSET = 11
    private const val INTERLACE_OFFSET = 12
    private const val GREYSCALE = 0
    private const val TRUECOLOUR = 2
    private const val INDEXED_COLOUR = 3
    private const val GREYSCALE_ALPHA = 4
    private const val TRUECOLOUR_ALPHA = 6
    private const val IHDR = 0x49484452L
    private const val IDAT = 0x49444154L
    private const val IEND = 0x49454e44L
    private const val CRC_POLYNOMIAL = 0xedb88320u
}
