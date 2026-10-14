package io.github.artemidius.blowhard.adapters.documents

internal fun ByteArray.unsignedByte(index: Int): Int = this[index].toInt() and BYTE_MASK

internal fun ByteArray.readUnsignedShort(index: Int): Int =
    (unsignedByte(index) shl BYTE_BITS) or unsignedByte(index + 1)

internal fun ByteArray.readUnsignedInt(index: Int): Long = (unsignedByte(index).toLong() shl THREE_BYTES_BITS) or
    (unsignedByte(index + 1).toLong() shl TWO_BYTES_BITS) or
    (unsignedByte(index + 2).toLong() shl BYTE_BITS) or
    unsignedByte(index + FOURTH_BYTE_OFFSET).toLong()

internal fun ByteArray.matchesBytes(offset: Int, expected: IntArray): Boolean =
    offset >= 0 && expected.size <= size && offset <= size - expected.size &&
        expected.indices.all { unsignedByte(offset + it) == expected[it] }

internal fun Int.isPdfWhitespace(): Boolean = this in PDF_WHITESPACE

internal fun Int.isPdfDelimiter(): Boolean = isPdfWhitespace() || this in PDF_DELIMITERS

private const val BYTE_MASK = 0xff
private const val BYTE_BITS = 8
private const val TWO_BYTES_BITS = 16
private const val THREE_BYTES_BITS = 24
private const val FOURTH_BYTE_OFFSET = 3
private val PDF_WHITESPACE = intArrayOf(0, 9, 10, 12, 13, 32)
private val PDF_DELIMITERS =
    intArrayOf('('.code, ')'.code, '<'.code, '>'.code, '['.code, ']'.code, '{'.code, '}'.code, '/'.code, '%'.code)
