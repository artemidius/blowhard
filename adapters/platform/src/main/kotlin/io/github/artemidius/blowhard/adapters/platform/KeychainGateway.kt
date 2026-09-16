package io.github.artemidius.blowhard.adapters.platform

import io.github.artemidius.blowhard.core.domain.CredentialAccessFailure

internal interface KeychainGateway {
    fun read(): KeychainRead

    fun replace(secret: ByteArray): KeychainMutation

    fun delete(): KeychainMutation
}

internal sealed interface KeychainRead {
    class Found(val bytes: ByteArray) : KeychainRead

    data object Missing : KeychainRead

    data class Unavailable(val reason: CredentialAccessFailure) : KeychainRead
}

internal sealed interface KeychainMutation {
    data object Success : KeychainMutation

    data class Unavailable(val reason: CredentialAccessFailure) : KeychainMutation
}
