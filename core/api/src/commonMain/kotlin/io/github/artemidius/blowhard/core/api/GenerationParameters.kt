package io.github.artemidius.blowhard.core.api

/**
 * Provider-neutral controls snapshotted when a generation attempt is accepted.
 *
 * A null value delegates that control to the selected model. The supported temperature range is
 * intentionally limited to Mistral's documented recommended range instead of exposing every wire
 * value accepted by a particular provider.
 */
public data class GenerationParameters(
    public val maxOutputTokens: Int? = null,
    public val temperature: Double? = null,
) {
    init {
        require(maxOutputTokens == null || maxOutputTokens in 1..MAX_OUTPUT_TOKENS) {
            "Maximum output tokens are outside the supported range"
        }
        require(temperature == null || (temperature.isFinite() && temperature in 0.0..MAX_TEMPERATURE)) {
            "Temperature is outside the supported range"
        }
    }
}

private const val MAX_OUTPUT_TOKENS = 1_000_000
private const val MAX_TEMPERATURE = 0.7
