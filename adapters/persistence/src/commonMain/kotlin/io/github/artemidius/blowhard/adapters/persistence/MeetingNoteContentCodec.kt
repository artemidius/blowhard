package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.api.MeetingNoteContent

internal object MeetingNoteContentCodec {
    fun encode(content: MeetingNoteContent): String = MeetingNoteContentWriter.encode(content)

    fun decode(raw: String): MeetingNoteContent? = MeetingNoteContentReader.decode(raw)
}
