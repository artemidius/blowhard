package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.DocumentContentDescriptor

/** Inspects selected bytes without trusting a filename or declared media type. */
public fun interface DocumentInspector {
    /** Returns verified characteristics or a content-safe rejection. */
    public suspend fun inspect(content: ByteArray): DocumentInspectionResult
}

/** Format-inspection result before application policy is applied. */
public sealed interface DocumentInspectionResult {
    /** Content was parsed into verified characteristics. */
    public data class Inspected(public val descriptor: DocumentContentDescriptor) : DocumentInspectionResult

    /** Content did not match a supported format. */
    public data object UnsupportedFormat : DocumentInspectionResult

    /** Content identified a supported format but was malformed or incomplete. */
    public data object InvalidContent : DocumentInspectionResult
}
