package io.github.artemidius.blowhard.core.api

import kotlin.jvm.JvmInline

/** Stable, opaque identity of one locally persisted meeting note. */
@JvmInline
public value class MeetingNoteId(public val value: String) {
    init {
        require(value.isNotBlank() && value.length <= MAX_LENGTH) { "Meeting-note ID is invalid" }
    }

    private companion object {
        const val MAX_LENGTH = 128
    }
}

/** Generated content and optional user revision retained as separate values. */
public data class MeetingNoteRecord(
    public val id: MeetingNoteId,
    public val sourceDocumentId: DocumentId,
    public val templateVersion: String,
    public val modelId: String,
    public val generatedContent: MeetingNoteContent,
    public val editedContent: MeetingNoteContent?,
    public val createdAtEpochMilliseconds: Long,
    public val updatedAtEpochMilliseconds: Long,
    public val revision: Long,
) {
    init {
        require(templateVersion.isNotBlank() && templateVersion.length <= MAX_TEMPLATE_VERSION_LENGTH) {
            "Template version is invalid"
        }
        require(modelId.isNotBlank() && modelId.length <= MAX_MODEL_ID_LENGTH && modelId.none(Char::isISOControl)) {
            "Model identity is invalid"
        }
        require(createdAtEpochMilliseconds >= 0) { "Creation time must not be negative" }
        require(updatedAtEpochMilliseconds >= createdAtEpochMilliseconds) { "Update time precedes creation" }
        require(revision >= 0) { "Revision must not be negative" }
        require((editedContent == null) == (revision == 0L)) { "Revision does not match edited content" }
    }

    /** Current user-visible content without losing the generated source value. */
    public val content: MeetingNoteContent get() = editedContent ?: generatedContent

    private companion object {
        const val MAX_TEMPLATE_VERSION_LENGTH = 128
        const val MAX_MODEL_ID_LENGTH = 256
    }
}

/** Typed meeting-note generation, editing, lookup, and export boundary. */
public interface MeetingNotes {
    /** Generates and persists one note from a durable OCR extraction. */
    public suspend fun generate(sourceDocumentId: DocumentId): MeetingNoteGenerationOutcome

    /** Looks up one local note. */
    public suspend fun find(id: MeetingNoteId): MeetingNoteLookupOutcome

    /** Replaces the editable value only when [expectedRevision] is current. */
    public suspend fun update(
        id: MeetingNoteId,
        expectedRevision: Long,
        content: MeetingNoteContent,
    ): MeetingNoteUpdateOutcome

    /** Produces a bounded, deterministic representation of the current value. */
    public suspend fun export(id: MeetingNoteId, format: MeetingNoteExportFormat): MeetingNoteExportOutcome
}

/** Safe terminal result of explicit meeting-note generation. */
public sealed interface MeetingNoteGenerationOutcome {
    /** A validated note was stored locally. */
    public data class Generated(public val note: MeetingNoteRecord) : MeetingNoteGenerationOutcome

    /** No note was published. */
    public data class Rejected(public val reason: MeetingNoteGenerationRejection) : MeetingNoteGenerationOutcome
}

/** Content-safe generation rejections. */
public enum class MeetingNoteGenerationRejection {
    DOCUMENT_NOT_FOUND,
    DOCUMENT_NOT_PROCESSED,
    EMPTY_SOURCE,
    INPUT_TOO_LARGE,
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    INVALID_OUTPUT,
    STORAGE_UNAVAILABLE,
}

/** Safe local note lookup. */
public sealed interface MeetingNoteLookupOutcome {
    /** A note was found. */
    public data class Found(public val note: MeetingNoteRecord) : MeetingNoteLookupOutcome

    /** No note matched the requested identity. */
    public data object NotFound : MeetingNoteLookupOutcome

    /** Local storage could not be read safely. */
    public data object StorageUnavailable : MeetingNoteLookupOutcome
}

/** Safe optimistic edit result. */
public sealed interface MeetingNoteUpdateOutcome {
    /** The edit was stored and the revision advanced. */
    public data class Updated(public val note: MeetingNoteRecord) : MeetingNoteUpdateOutcome

    /** No note matched the requested identity. */
    public data object NotFound : MeetingNoteUpdateOutcome

    /** The caller edited a stale revision. */
    public data object Conflict : MeetingNoteUpdateOutcome

    /** Local storage could not save the edit. */
    public data object StorageUnavailable : MeetingNoteUpdateOutcome
}

/** Supported explicit export representations. */
public enum class MeetingNoteExportFormat {
    JSON,
    MARKDOWN,
}

/** Bounded in-memory export ready for a separate platform-controlled save action. */
public data class MeetingNoteExport(
    public val suggestedFileName: String,
    public val mediaType: String,
    public val content: String,
) {
    init {
        require(suggestedFileName.isNotBlank() && '/' !in suggestedFileName && '\\' !in suggestedFileName) {
            "Export file name is invalid"
        }
        require(mediaType.isNotBlank()) { "Export media type is invalid" }
        require(content.isNotBlank() && content.length <= MAX_CONTENT_CHARACTERS) { "Export content is invalid" }
    }

    /** Maximum exported Kotlin characters. */
    public companion object {
        public const val MAX_CONTENT_CHARACTERS: Int = 512 * 1024
    }
}

/** Safe export outcome. */
public sealed interface MeetingNoteExportOutcome {
    /** A bounded representation was created. */
    public data class Exported(public val value: MeetingNoteExport) : MeetingNoteExportOutcome

    /** No note matched the requested identity. */
    public data object NotFound : MeetingNoteExportOutcome

    /** Local storage could not be read safely. */
    public data object StorageUnavailable : MeetingNoteExportOutcome

    /** The current value could not be represented within export limits. */
    public data object ContentTooLarge : MeetingNoteExportOutcome
}
