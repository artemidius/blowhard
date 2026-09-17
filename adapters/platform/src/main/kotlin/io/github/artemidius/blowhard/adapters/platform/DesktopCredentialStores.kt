package io.github.artemidius.blowhard.adapters.platform

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.Dispatchers

/** Creates credential stores backed only by verified desktop platform facilities. */
public object DesktopCredentialStores {
    /**
     * Creates the application store for the Mistral API key.
     *
     * macOS uses the login Keychain. Other desktop hosts fail closed until an OS-backed adapter is
     * implemented and verified; no file or preferences fallback is used.
     */
    public fun mistralApiKey(): CredentialStore = createMistralApiKey(System.getProperty("os.name").orEmpty())

    internal fun createMistralApiKey(osName: String): CredentialStore = if (osName.startsWith("Mac", true)) {
        KeychainCredentialStore(
            gateway = MacOsKeychainGateway(
                service = "io.github.artemidius.blowhard",
                account = "mistral-api-key",
            ),
            dispatcher = Dispatchers.IO,
        )
    } else {
        UnavailableCredentialStore
    }
}

private data object UnavailableCredentialStore : CredentialStore {
    override suspend fun read(): CredentialReadResult =
        CredentialReadResult.Unavailable(CredentialAccessFailure.PLATFORM_UNAVAILABLE)

    override suspend fun replace(secret: String): CredentialMutationResult =
        CredentialMutationResult.Unavailable(CredentialAccessFailure.PLATFORM_UNAVAILABLE)

    override suspend fun delete(): CredentialMutationResult =
        CredentialMutationResult.Unavailable(CredentialAccessFailure.PLATFORM_UNAVAILABLE)
}
