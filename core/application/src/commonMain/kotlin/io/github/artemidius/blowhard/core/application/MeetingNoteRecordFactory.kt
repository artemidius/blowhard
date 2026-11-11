package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.DocumentId
import io.github.artemidius.blowhard.core.api.MeetingNoteContent
import io.github.artemidius.blowhard.core.api.MeetingNoteRecord

internal object MeetingNoteRecordFactory {
    fun create(
        sourceDocumentId: DocumentId,
        modelId: String,
        content: MeetingNoteContent,
        idFactory: MeetingNoteIdFactory,
        createdAt: Long?,
    ): MeetingNoteRecord? = try {
        createdAt?.let {
            MeetingNoteRecord(
                id = idFactory.create(),
                sourceDocumentId = sourceDocumentId,
                templateVersion = MeetingNoteTemplateV1.VERSION,
                modelId = modelId,
                generatedContent = content,
                editedContent = null,
                createdAtEpochMilliseconds = it,
                updatedAtEpochMilliseconds = it,
                revision = 0,
            )
        }
    } catch (_: Exception) {
        null
    }
}
