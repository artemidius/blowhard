package io.github.artemidius.blowhard.adapters.mistral

import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore

/** Adapts provider-neutral secure storage to just-in-time Mistral credential reads. */
public fun CredentialStore.asMistralCredentialProvider(): MistralCredentialProvider = MistralCredentialProvider {
    when (val result = read()) {
        is CredentialReadResult.Available -> result.secret

        CredentialReadResult.Missing,
        is CredentialReadResult.Unavailable,
        -> null
    }
}
