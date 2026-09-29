package io.github.artemidius.blowhard.adapters.persistence

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.ConversationStore

/** Opens Android conversation metadata stores in the application's database directory. */
public object AndroidConversationStores {
    /** Opens or creates [name]. The caller owns the returned store. */
    public fun open(context: Context, name: String): ConversationStore {
        require(name.isNotBlank()) { "Database name must not be blank" }
        val driver = AndroidSqliteDriver(BlowhardDatabase.Schema, context.applicationContext, name)
        return SqlDelightConversationStore(driver)
    }
}
