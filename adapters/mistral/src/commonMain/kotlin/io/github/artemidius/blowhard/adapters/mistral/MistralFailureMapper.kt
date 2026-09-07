package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.ModelCatalogFailure
import io.github.artemidius.blowhard.core.domain.ModelCatalogFailureKind
import io.github.artemidius.blowhard.core.domain.ModelCatalogResult
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

internal fun HttpResponse.toFailure(): ModelCatalogResult.Failure {
    val kind = when {
        status.isSuccess() -> ModelCatalogFailureKind.INVALID_RESPONSE
        status.value in 300..399 -> ModelCatalogFailureKind.REDIRECT_REJECTED
        status == HttpStatusCode.Unauthorized -> ModelCatalogFailureKind.UNAUTHORIZED
        status == HttpStatusCode.Forbidden -> ModelCatalogFailureKind.FORBIDDEN
        status == HttpStatusCode.PaymentRequired -> ModelCatalogFailureKind.PAYMENT_REQUIRED
        status == HttpStatusCode.NotFound -> ModelCatalogFailureKind.NOT_FOUND
        status == HttpStatusCode.TooManyRequests -> ModelCatalogFailureKind.RATE_LIMITED
        status.value == 408 || status.value >= 500 -> ModelCatalogFailureKind.UNAVAILABLE
        else -> ModelCatalogFailureKind.INVALID_REQUEST
    }
    val retryAfter = headers[HttpHeaders.RetryAfter]
        ?.toLongOrNull()
        ?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }
    return ModelCatalogResult.Failure(ModelCatalogFailure(kind, retryAfter))
}

internal fun failure(kind: ModelCatalogFailureKind): ModelCatalogResult.Failure =
    ModelCatalogResult.Failure(ModelCatalogFailure(kind))

private const val MAX_RETRY_AFTER_SECONDS = 86_400L
