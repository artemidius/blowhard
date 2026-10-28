package io.github.artemidius.blowhard.adapters.mobile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface CredentialCipher {
    fun encrypt(plaintext: ByteArray): EncryptedCredential

    fun decrypt(encrypted: EncryptedCredential): ByteArray

    fun deleteKey()
}

internal class AndroidKeystoreCipher(private val keyAlias: String) : CredentialCipher {
    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun encrypt(plaintext: ByteArray): EncryptedCredential = try {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(CREDENTIAL_AAD)
        EncryptedCredential(cipher.iv.copyOf(), cipher.doFinal(plaintext))
    } catch (failure: KeyPermanentlyInvalidatedException) {
        throw CredentialKeyInvalidatedException(failure)
    }

    override fun decrypt(encrypted: EncryptedCredential): ByteArray = try {
        val key = keyStore.getKey(keyAlias, null) as? SecretKey
            ?: throw CredentialKeyMissingException()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, encrypted.iv))
        cipher.updateAAD(CREDENTIAL_AAD)
        cipher.doFinal(encrypted.ciphertext)
    } catch (failure: KeyPermanentlyInvalidatedException) {
        throw CredentialKeyInvalidatedException(failure)
    }

    override fun deleteKey() {
        val store = keyStore
        if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

internal class CredentialKeyInvalidatedException(cause: Throwable) : Exception(cause)

internal class CredentialKeyMissingException : Exception()

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_BITS = 256
private const val GCM_TAG_BITS = 128
private val CREDENTIAL_AAD = "io.github.artemidius.blowhard:mistral-api-key:v1".encodeToByteArray()
