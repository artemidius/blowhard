package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.MeetingNoteStore

/** Opens durable desktop meeting-note stores backed by SQLite. */
public object DesktopMeetingNoteStores {
    /** Opens or creates meeting-note storage at [path]. The caller must close the returned store. */
    public fun open(path: String): MeetingNoteStore {
        require(path.isNotBlank()) { "Database path must not be blank" }
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path", schema = BlowhardDatabase.Schema)
        return SqlDelightMeetingNoteStore(driver)
    }
}
