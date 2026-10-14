package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.DocumentStore

/** Opens durable desktop document stores backed by SQLite-owned binary content. */
public object DesktopDocumentStores {
    /** Opens or creates the document store at [path]. The caller must close the returned store. */
    public fun open(path: String): DocumentStore {
        require(path.isNotBlank()) { "Database path must not be blank" }
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$path",
            schema = BlowhardDatabase.Schema,
        )
        return SqlDelightDocumentStore(driver)
    }
}
