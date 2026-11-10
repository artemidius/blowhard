package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.core.domain.ConversationStore

/** Opens Apple-native conversation metadata stores backed by SQLite. */
public object NativeConversationStores {
    /** Opens or creates [name]. The caller owns the returned store. */
    public fun open(name: String): ConversationStore {
        require(name.isNotBlank()) { "Database name must not be blank" }
        return SqlDelightConversationStore(NativeSqliteDriver(BlowhardDatabase.Schema, name))
    }
}
