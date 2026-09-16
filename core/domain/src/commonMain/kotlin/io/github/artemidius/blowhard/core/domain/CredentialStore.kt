package io.github.artemidius.blowhard.core.domain

/** Secure storage for one application credential. */
public interface CredentialStore {
    /** Reads the credential without retaining it beyond the returned result. */
    public suspend fun read(): CredentialReadResult

    /** Adds or replaces the credential atomically where the platform permits. */
    public suspend fun replace(secret: String): CredentialMutationResult

    /** Removes the credential. Missing credentials are treated as successfully removed. */
    public suspend fun delete(): CredentialMutationResult
}

/** Result of reading a credential from secure platform storage. */
public sealed interface CredentialReadResult {
    /** A credential that should be consumed promptly and never logged. */
    public class Available(public val secret: String) : CredentialReadResult {
        override fun toString(): String = "Available(secret=<redacted>)"
    }

    /** No credential exists for this store. */
    public data object Missing : CredentialReadResult

    /** Secure storage could not provide a credential. */
    public data class Unavailable(public val reason: CredentialAccessFailure) : CredentialReadResult
}

/** Result of adding, replacing, or deleting a credential. */
public sealed interface CredentialMutationResult {
    /** The requested mutation completed. */
    public data object Success : CredentialMutationResult

    /** The supplied secret failed the store's safety constraints. */
    public data object Rejected : CredentialMutationResult

    /** Secure storage could not perform the mutation. */
    public data class Unavailable(public val reason: CredentialAccessFailure) : CredentialMutationResult
}

/** Sanitized reason why secure credential storage was unavailable. */
public enum class CredentialAccessFailure {
    ACCESS_DENIED,
    INTERACTION_REQUIRED,
    PLATFORM_UNAVAILABLE,
    UNEXPECTED,
}
