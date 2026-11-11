package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore

/** Opens durable desktop document-processing stores backed by SQLite. */
public object DesktopDocumentProcessingStores {
    /** Opens or creates processing storage at [path]. The caller must close the returned store. */
    public fun open(path: String): DocumentProcessingStore {
        require(path.isNotBlank()) { "Database path must not be blank" }
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$path",
            schema = BlowhardDatabase.Schema,
        )
        return SqlDelightDocumentProcessingStore(driver)
    }
}
