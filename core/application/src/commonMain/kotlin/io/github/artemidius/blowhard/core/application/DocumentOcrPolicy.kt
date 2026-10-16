package io.github.artemidius.blowhard.core.application

/** Resource ceilings applied before processor output reaches durable storage. */
public data class DocumentOcrPolicy(
    public val maximumPages: Int = DEFAULT_MAXIMUM_PAGES,
    public val maximumPageCharacters: Int = DEFAULT_MAXIMUM_PAGE_CHARACTERS,
    public val maximumTotalCharacters: Int = DEFAULT_MAXIMUM_TOTAL_CHARACTERS,
) {
    init {
        require(maximumPages > 0) { "Maximum OCR pages must be positive" }
        require(maximumPageCharacters > 0) { "Maximum page output must be positive" }
        require(maximumTotalCharacters >= maximumPageCharacters) {
            "Total OCR output must accept at least one maximum-sized page"
        }
    }

    /** Conservative output defaults for local review workflows. */
    public companion object {
        public const val DEFAULT_MAXIMUM_PAGES: Int = 100
        public const val DEFAULT_MAXIMUM_PAGE_CHARACTERS: Int = 256 * 1024
        public const val DEFAULT_MAXIMUM_TOTAL_CHARACTERS: Int = 2 * 1024 * 1024
    }
}
