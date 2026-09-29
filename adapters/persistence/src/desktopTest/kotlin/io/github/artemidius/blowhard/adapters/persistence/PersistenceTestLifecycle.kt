package io.github.artemidius.blowhard.adapters.persistence

import io.github.artemidius.blowhard.core.domain.ConversationStore
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

internal fun withTemporaryDatabase(block: suspend (String) -> Unit) {
    val path = Files.createTempFile("blowhard-conversation-", ".db")
    path.deleteIfExists()
    try {
        runTest { block(path.toString()) }
    } finally {
        path.deleteIfExists()
    }
}

internal suspend fun <T> ConversationStore.useStore(block: suspend (ConversationStore) -> T): T = try {
    block(this)
} finally {
    close()
}
