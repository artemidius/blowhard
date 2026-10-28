package io.github.artemidius.blowhard.adapters.mobile

import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.Dispatchers

/** iOS secure-storage factories for provider credentials. */
public object IosCredentialStores {
    /** Creates the app-scoped Mistral API-key store backed by the iOS Keychain. */
    public fun mistralApiKey(): CredentialStore = create(DEFAULT_SERVICE, DEFAULT_ACCOUNT)

    internal fun create(service: String, account: String): CredentialStore = IosKeychainCredentialStore(
        gateway = NativeIosKeychainGateway(service, account),
        dispatcher = Dispatchers.Default,
    )
}

private const val DEFAULT_SERVICE = "io.github.artemidius.blowhard"
private const val DEFAULT_ACCOUNT = "mistral-api-key.v1"
