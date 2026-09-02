package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.domain.GenerationFailure
import io.github.artemidius.blowhard.core.domain.GenerationFailureKind

internal fun GenerationFailure.toRunFailure(): RunFailure = RunFailure(
    kind = when (kind) {
        GenerationFailureKind.UNAUTHORIZED -> RunFailureKind.UNAUTHORIZED
        GenerationFailureKind.INVALID_REQUEST -> RunFailureKind.INVALID_REQUEST
        GenerationFailureKind.RATE_LIMITED -> RunFailureKind.RATE_LIMITED
        GenerationFailureKind.UNAVAILABLE -> RunFailureKind.UNAVAILABLE
        GenerationFailureKind.UNEXPECTED -> RunFailureKind.UNEXPECTED
    },
    reference = reference,
)
