package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore

/** Opens Apple-native meeting-note stores backed by SQLite. */
public object NativeMeetingNoteStores {
    /** Opens or creates [name]. The caller must close the returned store. */
    public fun open(name: String): MeetingNoteStore {
        require(name.isNotBlank()) { "Database name must not be blank" }
        return SqlDelightMeetingNoteStore(NativeSqliteDriver(BlowhardDatabase.Schema, name))
    }
}
