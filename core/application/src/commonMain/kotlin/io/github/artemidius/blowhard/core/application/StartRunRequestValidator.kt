package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.StartRunRejection
import io.github.artemidius.blowhard.core.api.StartRunRequest
import io.github.artemidius.blowhard.core.domain.PromptRejection
import io.github.artemidius.blowhard.core.domain.PromptText
import io.github.artemidius.blowhard.core.domain.PromptTextResult

internal class StartRunRequestValidator {
    fun validate(request: StartRunRequest): StartRunValidation = when (val prompt = PromptText.from(request.prompt)) {
        is PromptTextResult.Accepted -> StartRunValidation.Accepted(prompt.prompt)

        is PromptTextResult.Rejected ->
            when (prompt.reason) {
                PromptRejection.BLANK ->
                    StartRunValidation.Rejected(StartRunRejection.BLANK_PROMPT)
            }
    }
}

internal sealed interface StartRunValidation {
    data class Accepted(val prompt: PromptText) : StartRunValidation

    data class Rejected(val reason: StartRunRejection) : StartRunValidation
}
