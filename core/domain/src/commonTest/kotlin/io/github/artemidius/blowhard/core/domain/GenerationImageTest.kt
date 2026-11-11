package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerationImageTest {
    @Test
    fun `accepted image owns defensive bounded bytes`() {
        val source = byteArrayOf(1, 2, 3)
        val image = assertNotNull(GenerationImage.from(GenerationImageMediaType.PNG, source))

        source[0] = 9
        val firstRead = image.copyBytes()
        firstRead[1] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), image.copyBytes())
        assertEquals(3, image.sizeBytes)
        assertEquals("image/png", image.mediaType.value)
    }

    @Test
    fun `empty and oversized images are rejected before provider access`() {
        assertNull(GenerationImage.from(GenerationImageMediaType.JPEG, byteArrayOf()))
        assertNull(
            GenerationImage.from(
                GenerationImageMediaType.JPEG,
                ByteArray(MAX_GENERATION_IMAGE_BYTES + 1),
            ),
        )
    }
}
