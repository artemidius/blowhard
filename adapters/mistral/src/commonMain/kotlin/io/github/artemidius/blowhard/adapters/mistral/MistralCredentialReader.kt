package io.github.artemidius.blowhard.adapters.mistral

import kotlinx.coroutines.CancellationException

internal suspend fun MistralCredentialProvider.readSafeApiKey(): String? = try {
    apiKey()?.takeIf(String::isSafeBearerToken)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    null
}

private fun String.isSafeBearerToken(): Boolean =
    isNotBlank() && length <= MAX_BEARER_TOKEN_LENGTH && none { it.isWhitespace() || it.isISOControl() }

private const val MAX_BEARER_TOKEN_LENGTH = 4_096
