package io.github.artemidius.blowhard.adapters.persistence

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.DocumentStore

/** Opens Android document stores inside the application database directory. */
public object AndroidDocumentStores {
    /** Opens or creates [name]. The caller must close the returned store. */
    public fun open(context: Context, name: String): DocumentStore {
        require(name.isNotBlank()) { "Database name must not be blank" }
        val driver = AndroidSqliteDriver(BlowhardDatabase.Schema, context.applicationContext, name)
        return SqlDelightDocumentStore(driver)
    }
}
