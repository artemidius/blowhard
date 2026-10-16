package io.github.artemidius.blowhard.core.domain

import io.github.artemidius.blowhard.core.api.ImportedDocument

/** External or deterministic processor boundary for one verified local document. */
public fun interface DocumentOcrPort {
    /** Extracts ordered page text without owning persistence or retry policy. */
    public suspend fun process(request: DocumentOcrRequest): DocumentOcrPortResult
}

/** Immutable, defensively copied input to a document processor. */
public class DocumentOcrRequest(public val document: ImportedDocument, content: ByteArray) {
    private val value: ByteArray = content.copyOf()

    init {
        require(value.size.toLong() == document.sizeBytes) { "OCR content size does not match metadata" }
    }

    /** Returns an isolated copy of the inspected source bytes. */
    public fun copyContent(): ByteArray = value.copyOf()
}

/** Untrusted processor response before application validation. */
public sealed interface DocumentOcrPortResult {
    /** Processor output and its versioned implementation identity. */
    public data class Processed(public val processor: String, public val pages: List<DocumentOcrPage>) :
        DocumentOcrPortResult

    /** The processor failed without returning publishable output. */
    public data class Failed(public val kind: DocumentOcrFailureKind) : DocumentOcrPortResult
}

/** Untrusted page returned by a processor adapter. */
public data class DocumentOcrPage(public val index: Int, public val markdown: String)

/** Provider-neutral processor failure classification. */
public enum class DocumentOcrFailureKind {
    AUTHENTICATION,
    RATE_LIMITED,
    INPUT_REJECTED,
    REMOTE_FAILURE,
    INVALID_RESPONSE,
}
