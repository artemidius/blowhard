package io.github.artemidius.blowhard.adapters.mobile

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import io.github.artemidius.blowhard.core.domain.CredentialMutationResult
import io.github.artemidius.blowhard.core.domain.CredentialReadResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCredentialStoreDeviceTest {
    @Test
    fun keystoreBackedStoreSupportsIsolatedCredentialLifecycle(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val keyAlias = "io.github.artemidius.blowhard.test.$suffix"
        val preferencesName = "credential-test-$suffix"
        val store = AndroidCredentialStores.create(context, keyAlias, preferencesName)
        try {
            assertEquals(CredentialReadResult.Missing, store.read())
            assertEquals(CredentialMutationResult.Success, store.replace("first-synthetic-key"))
            assertEquals("first-synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)
            assertEquals(CredentialMutationResult.Success, store.replace("second-synthetic-key"))
            assertEquals("second-synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)

            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit()
                .putString(RECORD_KEY, "corrupt")
                .commit()
            assertEquals(
                CredentialReadResult.Unavailable(CredentialAccessFailure.UNEXPECTED),
                store.read(),
            )

            assertEquals(CredentialMutationResult.Success, store.replace("recovered-synthetic-key"))
            assertEquals("recovered-synthetic-key", assertIs<CredentialReadResult.Available>(store.read()).secret)
            assertEquals(CredentialMutationResult.Success, store.delete())
            assertEquals(CredentialReadResult.Missing, store.read())
        } finally {
            store.delete()
            context.deleteSharedPreferences(preferencesName)
        }
    }
}
