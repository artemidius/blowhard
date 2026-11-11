package io.github.artemidius.blowhard.core.application

/** Resource ceilings enforced before app-managed document storage. */
public data class DocumentImportPolicy(
    public val maximumBytes: Long = DEFAULT_MAXIMUM_BYTES,
    public val maximumPdfPages: Int = DEFAULT_MAXIMUM_PDF_PAGES,
    public val maximumImagePixels: Long = DEFAULT_MAXIMUM_IMAGE_PIXELS,
) {
    init {
        require(maximumBytes in 1..Int.MAX_VALUE.toLong()) { "Maximum bytes are outside the supported range" }
        require(maximumPdfPages > 0) { "Maximum PDF pages must be positive" }
        require(maximumImagePixels > 0) { "Maximum image pixels must be positive" }
    }

    /** Conservative defaults for local portfolio workflows. */
    public companion object {
        public const val DEFAULT_MAXIMUM_BYTES: Long = 20L * 1024L * 1024L
        public const val DEFAULT_MAXIMUM_PDF_PAGES: Int = 100
        public const val DEFAULT_MAXIMUM_IMAGE_PIXELS: Long = 40_000_000L
    }
}
