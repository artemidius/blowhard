package io.github.artemidius.blowhard.adapters.mobile

import android.content.SharedPreferences
import android.util.Base64

internal sealed interface StoredCredentialRecord {
    data object Missing : StoredCredentialRecord

    data object Corrupt : StoredCredentialRecord

    class Available(val encrypted: EncryptedCredential) : StoredCredentialRecord
}

internal class EncryptedCredential(val iv: ByteArray, val ciphertext: ByteArray) {
    fun clear() {
        iv.fill(0)
        ciphertext.fill(0)
    }
}

internal interface CredentialRecordRepository {
    fun read(): StoredCredentialRecord

    fun write(encrypted: EncryptedCredential): Boolean

    fun delete(): Boolean
}

internal class SharedPreferencesCredentialRecords(private val preferences: SharedPreferences) :
    CredentialRecordRepository {
    override fun read(): StoredCredentialRecord {
        val serialized = preferences.getString(RECORD_KEY, null) ?: return StoredCredentialRecord.Missing
        val parts = serialized.split(RECORD_SEPARATOR, limit = RECORD_PARTS)
        return if (parts.size == RECORD_PARTS && parts[0] == RECORD_VERSION) {
            decode(parts)
        } else {
            StoredCredentialRecord.Corrupt
        }
    }

    private fun decode(parts: List<String>): StoredCredentialRecord = try {
        val iv = Base64.decode(parts[1], BASE64_FLAGS)
        val ciphertext = Base64.decode(parts[2], BASE64_FLAGS)
        if (iv.size != GCM_IV_BYTES || ciphertext.size !in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES) {
            iv.fill(0)
            ciphertext.fill(0)
            StoredCredentialRecord.Corrupt
        } else {
            StoredCredentialRecord.Available(EncryptedCredential(iv, ciphertext))
        }
    } catch (_: IllegalArgumentException) {
        StoredCredentialRecord.Corrupt
    }

    override fun write(encrypted: EncryptedCredential): Boolean = if (encrypted.isValid()) {
        preferences.edit().putString(RECORD_KEY, encrypted.serialize()).commit()
    } else {
        false
    }

    private fun EncryptedCredential.isValid(): Boolean =
        iv.size == GCM_IV_BYTES && ciphertext.size in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES

    private fun EncryptedCredential.serialize(): String = buildString {
        append(RECORD_VERSION)
        append(RECORD_SEPARATOR)
        append(Base64.encodeToString(iv, BASE64_FLAGS))
        append(RECORD_SEPARATOR)
        append(Base64.encodeToString(ciphertext, BASE64_FLAGS))
    }

    override fun delete(): Boolean = preferences.edit().remove(RECORD_KEY).commit()
}

internal const val RECORD_KEY: String = "mistral_api_key_record"
private const val RECORD_VERSION = "1"
private const val RECORD_SEPARATOR = '|'
private const val RECORD_PARTS = 3
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BYTES = 16
private const val MIN_CIPHERTEXT_BYTES = GCM_TAG_BYTES + 1
private const val MAX_CIPHERTEXT_BYTES = MAX_CREDENTIAL_BYTES + GCM_TAG_BYTES
private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING
