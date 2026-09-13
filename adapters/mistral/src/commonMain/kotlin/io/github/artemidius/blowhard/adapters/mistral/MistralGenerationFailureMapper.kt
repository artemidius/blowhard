package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRetryAdvice
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

internal fun HttpResponse.toGenerationFailure(): GenerationFailure {
    val kind = when {
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
            GenerationFailureKind.UNAUTHORIZED

        status == HttpStatusCode.TooManyRequests -> GenerationFailureKind.RATE_LIMITED

        status.value == 408 || status.value >= 500 -> GenerationFailureKind.UNAVAILABLE

        status.value in 300..399 -> GenerationFailureKind.INVALID_RESPONSE

        else -> GenerationFailureKind.INVALID_REQUEST
    }
    val retryAfter = if (kind == GenerationFailureKind.RATE_LIMITED) {
        headers[HttpHeaders.RetryAfter]
            ?.toLongOrNull()
            ?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }
    } else {
        null
    }
    val advice = when {
        kind == GenerationFailureKind.RATE_LIMITED && retryAfter != null -> GenerationRetryAdvice.RETRY_AFTER_DELAY

        kind == GenerationFailureKind.RATE_LIMITED || kind == GenerationFailureKind.UNAVAILABLE ->
            GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT

        else -> GenerationRetryAdvice.DO_NOT_RETRY
    }
    return GenerationFailure(kind, retryAdvice = advice, retryAfterSeconds = retryAfter)
}

private const val MAX_RETRY_AFTER_SECONDS = 86_400L
