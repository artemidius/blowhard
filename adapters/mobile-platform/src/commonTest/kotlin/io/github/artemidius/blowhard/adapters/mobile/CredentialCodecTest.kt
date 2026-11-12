package io.github.artemidius.blowhard.adapters.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialCodecTest {
    @Test
    fun `safe credential survives a strict utf8 round trip`() {
        val encoded = CredentialCodec.encode("synthetic-key")

        assertEquals("synthetic-key", encoded?.let(CredentialCodec::decode))
    }

    @Test
    fun `blank whitespace control and oversized credentials are rejected`() {
        assertNull(CredentialCodec.encode(""))
        assertNull(CredentialCodec.encode("unsafe key"))
        assertNull(CredentialCodec.encode("line\nbreak"))
        assertNull(CredentialCodec.encode("x".repeat(MAX_CREDENTIAL_BYTES + 1)))
        assertNull(CredentialCodec.encode("é".repeat(MAX_CREDENTIAL_BYTES / 2 + 1)))
    }

    @Test
    fun `malformed or oversized utf8 is rejected`() {
        assertNull(CredentialCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(CredentialCodec.decode(ByteArray(MAX_CREDENTIAL_BYTES + 1)))
    }
}
