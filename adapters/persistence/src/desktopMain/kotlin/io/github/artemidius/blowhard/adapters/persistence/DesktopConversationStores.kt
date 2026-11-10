package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.ConversationStore

/** Opens durable desktop conversation metadata stores backed by SQLite. */
public object DesktopConversationStores {
    /** Opens or creates the database at [path]. The caller owns the returned store. */
    public fun open(path: String): ConversationStore {
        require(path.isNotBlank()) { "Database path must not be blank" }
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$path",
            schema = BlowhardDatabase.Schema,
        )
        return SqlDelightConversationStore(driver)
    }
}
