package io.github.artemidius.blowhard.adapters.persistence

import app.cash.sqldelight.db.SqlDriver
import io.github.artemidius.blowhard.adapters.persistence.db.BlowhardDatabase
import io.github.artemidius.blowhard.adapters.persistence.db.Conversation
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.ConversationStore
import io.github.artemidius.blowhard.core.domain.StoredConversation
import io.github.artemidius.blowhard.core.domain.StoredTranscript
import io.github.artemidius.blowhard.core.domain.TranscriptSaveResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SqlDelightConversationStore(private val driver: SqlDriver) : ConversationStore {
    private val database = BlowhardDatabase(driver)
    private val queries = database.conversationQueries
    private val access = Mutex()
    private var closed = false

    override suspend fun save(conversation: StoredConversation): Unit = access.withLock {
        requireOpen()
        queries.transaction {
            queries.insertConversation(
                id = conversation.id.value,
                title = conversation.title,
                created_at_epoch_ms = conversation.createdAtEpochMilliseconds,
                updated_at_epoch_ms = conversation.updatedAtEpochMilliseconds,
            )
            queries.updateConversation(
                title = conversation.title,
                updated_at_epoch_ms = conversation.updatedAtEpochMilliseconds,
                id = conversation.id.value,
            )
        }
    }

    override suspend fun find(id: ConversationId): StoredConversation? = access.withLock {
        requireOpen()
        queries.findConversation(id.value).executeAsOneOrNull()?.toStoredConversation()
    }

    override suspend fun list(): List<StoredConversation> = access.withLock {
        requireOpen()
        queries.listConversations().executeAsList().map(Conversation::toStoredConversation)
    }

    override suspend fun saveTranscript(transcript: StoredTranscript): TranscriptSaveResult = access.withLock {
        requireOpen()
        if (queries.findConversation(transcript.conversationId.value).executeAsOneOrNull() == null) {
            return@withLock TranscriptSaveResult.CONVERSATION_NOT_FOUND
        }
        database.transcriptQueries.transaction {
            database.transcriptQueries.replace(transcript)
        }
        TranscriptSaveResult.SAVED
    }

    override suspend fun loadTranscript(id: ConversationId): StoredTranscript? = access.withLock {
        requireOpen()
        if (queries.findConversation(id.value).executeAsOneOrNull() == null) {
            return@withLock null
        }
        database.transcriptQueries.load(id)
    }

    override suspend fun delete(id: ConversationId): Boolean = access.withLock {
        requireOpen()
        var deleted = false
        database.transcriptQueries.transaction {
            database.transcriptQueries.deleteOwnedRows(id)
            deleted = queries.deleteConversation(id.value).value > 0
        }
        deleted
    }

    override suspend fun close(): Unit = access.withLock {
        if (closed) return
        closed = true
        driver.close()
    }

    private fun requireOpen() {
        check(!closed) { "Conversation store is closed" }
    }
}

private fun Conversation.toStoredConversation(): StoredConversation = StoredConversation(
    id = ConversationId(id),
    title = title,
    createdAtEpochMilliseconds = created_at_epoch_ms,
    updatedAtEpochMilliseconds = updated_at_epoch_ms,
)
