package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.MeetingNoteId

/** Supplies opaque local meeting-note identities. */
public fun interface MeetingNoteIdFactory {
    /** Returns a new identity or fails without publishing one. */
    public fun create(): MeetingNoteId
}

/** Supplies meeting-note persistence timestamps. */
public fun interface MeetingNoteClock {
    /** Returns a non-negative Unix epoch timestamp in milliseconds. */
    public fun nowEpochMilliseconds(): Long
}
