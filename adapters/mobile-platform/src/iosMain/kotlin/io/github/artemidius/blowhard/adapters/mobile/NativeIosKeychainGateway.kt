@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.artemidius.blowhard.adapters.mobile

import io.github.artemidius.blowhard.adapters.mobile.keychain.BlowhardKeychainDelete
import io.github.artemidius.blowhard.adapters.mobile.keychain.BlowhardKeychainRead
import io.github.artemidius.blowhard.adapters.mobile.keychain.BlowhardKeychainReplace
import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned

internal sealed interface IosKeychainRead {
    class Found(val bytes: ByteArray) : IosKeychainRead

    data object Missing : IosKeychainRead

    data class Unavailable(val reason: CredentialAccessFailure) : IosKeychainRead
}

internal sealed interface IosKeychainMutation {
    data object Success : IosKeychainMutation

    data class Unavailable(val reason: CredentialAccessFailure) : IosKeychainMutation
}

internal interface IosKeychainGateway {
    fun read(): IosKeychainRead

    fun replace(secret: ByteArray): IosKeychainMutation

    fun delete(): IosKeychainMutation
}

internal class NativeIosKeychainGateway(private val service: String, private val account: String) :
    IosKeychainGateway {
    override fun read(): IosKeychainRead {
        val buffer = ByteArray(MAX_CREDENTIAL_BYTES)
        return try {
            val result = buffer.usePinned { pinned ->
                BlowhardKeychainRead(
                    service,
                    account,
                    pinned.addressOf(0).reinterpret(),
                    buffer.size,
                )
            }
            when {
                result > 0 -> IosKeychainRead.Found(buffer.copyOf(result))
                result == 0 -> IosKeychainRead.Missing
                else -> IosKeychainRead.Unavailable(result.toAccessFailure())
            }
        } finally {
            buffer.fill(0)
        }
    }

    override fun replace(secret: ByteArray): IosKeychainMutation {
        if (secret.isEmpty() || secret.size > MAX_CREDENTIAL_BYTES) return unexpectedMutation()
        val status = secret.usePinned { pinned ->
            BlowhardKeychainReplace(
                service,
                account,
                pinned.addressOf(0).reinterpret(),
                secret.size,
            )
        }
        return status.toMutation()
    }

    override fun delete(): IosKeychainMutation {
        val status = BlowhardKeychainDelete(service, account)
        return if (status == ERR_SEC_SUCCESS || status == ERR_SEC_ITEM_NOT_FOUND) {
            IosKeychainMutation.Success
        } else {
            IosKeychainMutation.Unavailable(status.toAccessFailure())
        }
    }
}

private fun Int.toMutation(): IosKeychainMutation = if (this == ERR_SEC_SUCCESS) {
    IosKeychainMutation.Success
} else {
    IosKeychainMutation.Unavailable(toAccessFailure())
}

private fun Int.toAccessFailure(): CredentialAccessFailure = when (this) {
    ERR_SEC_AUTH_FAILED, ERR_SEC_USER_CANCELED, ERR_SEC_MISSING_ENTITLEMENT ->
        CredentialAccessFailure.ACCESS_DENIED

    ERR_SEC_INTERACTION_NOT_ALLOWED -> CredentialAccessFailure.INTERACTION_REQUIRED

    ERR_SEC_NOT_AVAILABLE -> CredentialAccessFailure.PLATFORM_UNAVAILABLE

    else -> CredentialAccessFailure.UNEXPECTED
}

private fun unexpectedMutation(): IosKeychainMutation.Unavailable =
    IosKeychainMutation.Unavailable(CredentialAccessFailure.UNEXPECTED)

private const val ERR_SEC_SUCCESS = 0
private const val ERR_SEC_USER_CANCELED = -128
private const val ERR_SEC_NOT_AVAILABLE = -25_291
private const val ERR_SEC_AUTH_FAILED = -25_293
private const val ERR_SEC_ITEM_NOT_FOUND = -25_300
private const val ERR_SEC_INTERACTION_NOT_ALLOWED = -25_308
private const val ERR_SEC_MISSING_ENTITLEMENT = -34_018
