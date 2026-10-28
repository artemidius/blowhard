package io.github.artemidius.blowhard.adapters.mobile

import android.content.Context
import io.github.artemidius.blowhard.core.domain.CredentialStore
import kotlinx.coroutines.Dispatchers

/** Android secure-storage factories for provider credentials. */
public object AndroidCredentialStores {
    /** Creates the app-scoped Mistral API-key store backed by Android Keystore. */
    public fun mistralApiKey(context: Context): CredentialStore = create(
        context = context.applicationContext,
        keyAlias = DEFAULT_KEY_ALIAS,
        preferencesName = DEFAULT_PREFERENCES_NAME,
    )

    internal fun create(context: Context, keyAlias: String, preferencesName: String): CredentialStore =
        AndroidKeystoreCredentialStore(
            cipher = AndroidKeystoreCipher(keyAlias),
            records = SharedPreferencesCredentialRecords(
                context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE),
            ),
            dispatcher = Dispatchers.IO,
        )
}

private const val DEFAULT_KEY_ALIAS = "io.github.artemidius.blowhard.mistral-api-key.v1"
private const val DEFAULT_PREFERENCES_NAME = "blowhard_secure_credentials"
