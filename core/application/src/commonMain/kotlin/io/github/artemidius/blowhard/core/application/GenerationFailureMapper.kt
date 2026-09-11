package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunRetryAdvice
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationRetryAdvice

internal fun GenerationFailure.toRunFailure(): RunFailure = RunFailure(
    kind = when (kind) {
        GenerationFailureKind.CREDENTIAL_UNAVAILABLE -> RunFailureKind.CREDENTIAL_UNAVAILABLE
        GenerationFailureKind.UNAUTHORIZED -> RunFailureKind.UNAUTHORIZED
        GenerationFailureKind.INVALID_REQUEST -> RunFailureKind.INVALID_REQUEST
        GenerationFailureKind.RATE_LIMITED -> RunFailureKind.RATE_LIMITED
        GenerationFailureKind.UNAVAILABLE -> RunFailureKind.UNAVAILABLE
        GenerationFailureKind.INVALID_RESPONSE -> RunFailureKind.INVALID_RESPONSE
        GenerationFailureKind.UNEXPECTED -> RunFailureKind.UNEXPECTED
    },
    reference = reference,
    retryAdvice = when (retryAdvice) {
        GenerationRetryAdvice.DO_NOT_RETRY -> RunRetryAdvice.DO_NOT_RETRY
        GenerationRetryAdvice.EXPLICIT_NEW_ATTEMPT -> RunRetryAdvice.EXPLICIT_NEW_ATTEMPT
        GenerationRetryAdvice.RETRY_AFTER_DELAY -> RunRetryAdvice.RETRY_AFTER_DELAY
    },
    retryAfterSeconds = retryAfterSeconds,
)
