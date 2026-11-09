package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunSnapshot

internal data class PresentedSnapshot(
    val output: String,
    val runState: ConversationRunState,
    val delivery: MessageDelivery,
    val notice: UiNotice? = null,
    val terminal: Boolean,
)

internal fun RunSnapshot.present(): PresentedSnapshot = when (this) {
    is RunSnapshot.Queued -> PresentedSnapshot(
        output = "",
        runState = ConversationRunState.QUEUED,
        delivery = MessageDelivery.STREAMING,
        terminal = false,
    )

    is RunSnapshot.Running -> PresentedSnapshot(
        output = output,
        runState = ConversationRunState.STREAMING,
        delivery = MessageDelivery.STREAMING,
        terminal = false,
    )

    is RunSnapshot.Succeeded -> PresentedSnapshot(
        output = output,
        runState = ConversationRunState.SUCCEEDED,
        delivery = MessageDelivery.COMPLETE,
        terminal = true,
    )

    is RunSnapshot.Failed -> PresentedSnapshot(
        output = partialOutput,
        runState = ConversationRunState.FAILED,
        delivery = MessageDelivery.FAILED,
        notice = UiNotice(failure.kind.safeMessage()),
        terminal = true,
    )

    is RunSnapshot.Cancelled -> PresentedSnapshot(
        output = partialOutput,
        runState = ConversationRunState.CANCELLED,
        delivery = MessageDelivery.CANCELLED,
        terminal = true,
    )
}

private fun RunFailureKind.safeMessage(): String = when (this) {
    RunFailureKind.CREDENTIAL_UNAVAILABLE -> "A provider credential is not available."
    RunFailureKind.UNAUTHORIZED -> "The provider rejected the configured credential."
    RunFailureKind.INVALID_REQUEST -> "The provider rejected this request."
    RunFailureKind.RATE_LIMITED -> "The provider is rate limiting requests. Try again later."
    RunFailureKind.UNAVAILABLE -> "The provider is temporarily unavailable."
    RunFailureKind.INVALID_RESPONSE -> "The provider returned an invalid streaming response."
    RunFailureKind.UNEXPECTED -> "Generation stopped unexpectedly. No sensitive details were exposed."
}
