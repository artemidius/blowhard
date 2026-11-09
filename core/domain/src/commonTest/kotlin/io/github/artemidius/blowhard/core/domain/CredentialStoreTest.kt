package io.github.artemidius.blowhard.core.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialStoreTest {
    @Test
    fun `available credential is redacted from diagnostics`() {
        val secret = "synthetic-private-value"

        val rendered = CredentialReadResult.Available(secret).toString()

        assertFalse(rendered.contains(secret))
        assertTrue(rendered.contains("redacted"))
    }
}
