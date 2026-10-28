package io.github.artemidius.blowhard.adapters.mobile

internal object CredentialCodec {
    fun encode(secret: String): ByteArray? = if (secret.hasSafeShape()) encodeValidShape(secret) else null

    fun decode(encoded: ByteArray): String? =
        if (encoded.isNotEmpty() && encoded.size <= MAX_CREDENTIAL_BYTES) decodeValidSize(encoded) else null

    private fun encodeValidShape(secret: String): ByteArray? {
        val encoded = secret.encodeToByteArray()
        return if (encoded.size <= MAX_CREDENTIAL_BYTES) {
            encoded
        } else {
            encoded.fill(0)
            null
        }
    }

    private fun decodeValidSize(encoded: ByteArray): String? = try {
        val secret = encoded.decodeToString(throwOnInvalidSequence = true)
        encode(secret)?.let { reencoded ->
            try {
                secret.takeIf { reencoded.contentEquals(encoded) }
            } finally {
                reencoded.fill(0)
            }
        }
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun String.hasSafeShape(): Boolean =
    isNotBlank() && length <= MAX_CREDENTIAL_BYTES && none { it.isWhitespace() || it.isISOControl() }

internal const val MAX_CREDENTIAL_BYTES: Int = 4_096
