package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.DocumentProcessingStore

/** Opens Apple-native document-processing stores backed by SQLite. */
public object NativeDocumentProcessingStores {
    /** Opens or creates [name]. The caller must close the returned store. */
    public fun open(name: String): DocumentProcessingStore {
        require(name.isNotBlank()) { "Database name must not be blank" }
        return SqlDelightDocumentProcessingStore(NativeSqliteDriver(BlowhardDatabase.Schema, name))
    }
}
