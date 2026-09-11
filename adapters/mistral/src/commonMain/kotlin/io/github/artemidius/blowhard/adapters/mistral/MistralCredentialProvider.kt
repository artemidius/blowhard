package io.github.artemidius.blowhard.adapters.mistral

/** Supplies an ephemeral Mistral API key for one outbound request. */
public fun interface MistralCredentialProvider {
    /** Returns the current key, or `null` when secure credential access is unavailable. */
    public suspend fun apiKey(): String?
}
