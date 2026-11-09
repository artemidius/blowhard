@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.artemidius.blowhard.adapters.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.mac.CoreFoundation.CFDataRef
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFIndex
import com.sun.jna.platform.mac.CoreFoundation.CFMutableDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import com.sun.jna.platform.mac.CoreFoundation.CFTypeRef
import com.sun.jna.ptr.PointerByReference
import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure

internal class MacOsKeychainGateway(
    private val service: String,
    private val account: String,
    private val native: KeychainNative = JnaKeychainNative,
) : KeychainGateway {
    override fun read(): KeychainRead {
        val result = native.read(service, account)
        return when (result.status) {
            ERR_SEC_SUCCESS -> result.bytes?.let(KeychainRead::Found) ?: unexpectedRead()
            ERR_SEC_ITEM_NOT_FOUND -> KeychainRead.Missing
            else -> KeychainRead.Unavailable(result.status.toAccessFailure())
        }
    }

    override fun replace(secret: ByteArray): KeychainMutation {
        val updateStatus = native.update(service, account, secret)
        return when (updateStatus) {
            ERR_SEC_SUCCESS -> KeychainMutation.Success
            ERR_SEC_ITEM_NOT_FOUND -> addOrResolveRace(secret)
            else -> unavailableMutation(updateStatus)
        }
    }

    private fun addOrResolveRace(secret: ByteArray): KeychainMutation =
        when (val status = native.add(service, account, secret)) {
            ERR_SEC_SUCCESS -> KeychainMutation.Success
            ERR_SEC_DUPLICATE_ITEM -> native.update(service, account, secret).toMutation()
            else -> unavailableMutation(status)
        }

    override fun delete(): KeychainMutation = when (val status = native.delete(service, account)) {
        ERR_SEC_SUCCESS, ERR_SEC_ITEM_NOT_FOUND -> KeychainMutation.Success
        else -> unavailableMutation(status)
    }
}

internal data class NativeReadResult(val status: Int, val bytes: ByteArray? = null)

internal interface KeychainNative {
    fun read(service: String, account: String): NativeReadResult

    fun add(service: String, account: String, secret: ByteArray): Int

    fun update(service: String, account: String, secret: ByteArray): Int

    fun delete(service: String, account: String): Int
}

private object JnaKeychainNative : KeychainNative {
    private val security = Native.load("Security", SecurityFramework::class.java)

    override fun read(service: String, account: String): NativeReadResult = OwnedDictionary().use { query ->
        query.putConstant("kSecClass", "kSecClassGenericPassword")
        query.putString("kSecAttrService", service)
        query.putString("kSecAttrAccount", account)
        query.putConstant("kSecReturnData", "kCFBooleanTrue", Framework.CORE_FOUNDATION)
        query.putConstant("kSecMatchLimit", "kSecMatchLimitOne")

        val result = PointerByReference()
        val status = security.SecItemCopyMatching(query.value, result)
        val pointer = result.value
        if (status != ERR_SEC_SUCCESS || pointer == null) return@use NativeReadResult(status)

        val data = CFDataRef(pointer)
        try {
            NativeReadResult(status, data.bytePtr.getByteArray(0, data.length))
        } finally {
            data.release()
        }
    }

    override fun add(service: String, account: String, secret: ByteArray): Int =
        credentialAttributes(service, account, secret).use { security.SecItemAdd(it.value, null) }

    override fun update(service: String, account: String, secret: ByteArray): Int =
        keychainQuery(service, account).use { query ->
            OwnedDictionary().use { attributes ->
                attributes.putData("kSecValueData", secret)
                security.SecItemUpdate(query.value, attributes.value)
            }
        }

    override fun delete(service: String, account: String): Int =
        keychainQuery(service, account).use { security.SecItemDelete(it.value) }

    private fun keychainQuery(service: String, account: String): OwnedDictionary = OwnedDictionary().apply {
        putConstant("kSecClass", "kSecClassGenericPassword")
        putString("kSecAttrService", service)
        putString("kSecAttrAccount", account)
    }

    private fun credentialAttributes(service: String, account: String, secret: ByteArray): OwnedDictionary =
        keychainQuery(service, account).apply { putData("kSecValueData", secret) }
}

private interface SecurityFramework : Library {
    fun SecItemCopyMatching(query: CFDictionaryRef, result: PointerByReference): Int

    fun SecItemAdd(attributes: CFDictionaryRef, result: PointerByReference?): Int

    fun SecItemUpdate(query: CFDictionaryRef, attributesToUpdate: CFDictionaryRef): Int

    fun SecItemDelete(query: CFDictionaryRef): Int
}

private class OwnedDictionary : AutoCloseable {
    private val references = mutableListOf<CFTypeRef>()
    val value: CFMutableDictionaryRef = JnaLibraries.coreFoundation.CFDictionaryCreateMutable(
        null,
        CFIndex(0),
        JnaLibraries.coreFoundationLibrary.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
        JnaLibraries.coreFoundationLibrary.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"),
    )

    fun putConstant(key: String, constant: String, framework: Framework = Framework.SECURITY) {
        value.setValue(frameworkSymbol(Framework.SECURITY, key), frameworkSymbol(framework, constant))
    }

    fun putString(key: String, text: String) {
        val string = CFStringRef.createCFString(text)
        references += string
        value.setValue(frameworkSymbol(Framework.SECURITY, key), string)
    }

    fun putData(key: String, bytes: ByteArray) {
        val memory = Memory(bytes.size.toLong())
        try {
            memory.write(0, bytes, 0, bytes.size)
            val data = JnaLibraries.coreFoundation.CFDataCreate(null, memory, CFIndex(bytes.size.toLong()))
            references += data
            value.setValue(frameworkSymbol(Framework.SECURITY, key), data)
        } finally {
            memory.clear()
            memory.close()
        }
    }

    override fun close() {
        value.release()
        references.forEach(CFTypeRef::release)
    }
}

private enum class Framework { SECURITY, CORE_FOUNDATION }

private fun frameworkSymbol(framework: Framework, name: String): PointerType {
    val library = when (framework) {
        Framework.SECURITY -> JnaLibraries.securityLibrary
        Framework.CORE_FOUNDATION -> JnaLibraries.coreFoundationLibrary
    }
    val pointer = library.getGlobalVariableAddress(name).getPointer(0)
    return CFTypeRef(pointer)
}

private fun Int.toMutation(): KeychainMutation = if (this == ERR_SEC_SUCCESS) {
    KeychainMutation.Success
} else {
    unavailableMutation(this)
}

private fun unavailableMutation(status: Int): KeychainMutation.Unavailable =
    KeychainMutation.Unavailable(status.toAccessFailure())

private fun unexpectedRead(): KeychainRead.Unavailable = KeychainRead.Unavailable(CredentialAccessFailure.UNEXPECTED)

private fun Int.toAccessFailure(): CredentialAccessFailure = when (this) {
    ERR_SEC_AUTH_FAILED, ERR_SEC_USER_CANCELED, ERR_SEC_MISSING_ENTITLEMENT ->
        CredentialAccessFailure.ACCESS_DENIED

    ERR_SEC_INTERACTION_NOT_ALLOWED -> CredentialAccessFailure.INTERACTION_REQUIRED

    ERR_SEC_NOT_AVAILABLE -> CredentialAccessFailure.PLATFORM_UNAVAILABLE

    else -> CredentialAccessFailure.UNEXPECTED
}

private object JnaLibraries {
    val coreFoundation: CoreFoundation = CoreFoundation.INSTANCE
    val coreFoundationLibrary: NativeLibrary = NativeLibrary.getInstance("CoreFoundation")
    val securityLibrary: NativeLibrary = NativeLibrary.getInstance("Security")
}

private const val ERR_SEC_SUCCESS = 0
private const val ERR_SEC_USER_CANCELED = -128
private const val ERR_SEC_NOT_AVAILABLE = -25_291
private const val ERR_SEC_AUTH_FAILED = -25_293
private const val ERR_SEC_DUPLICATE_ITEM = -25_299
private const val ERR_SEC_ITEM_NOT_FOUND = -25_300
private const val ERR_SEC_INTERACTION_NOT_ALLOWED = -25_308
private const val ERR_SEC_MISSING_ENTITLEMENT = -34_018
