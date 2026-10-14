package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.DocumentStore

/** Opens Apple-native document stores backed by SQLite-owned binary content. */
public object NativeDocumentStores {
    /** Opens or creates [name]. The caller must close the returned store. */
    public fun open(name: String): DocumentStore {
        require(name.isNotBlank()) { "Database name must not be blank" }
        return SqlDelightDocumentStore(NativeSqliteDriver(BlowhardDatabase.Schema, name))
    }
}
