package io.github.artemidius.blowhard.core.application

import io.github.artemidius.blowhard.core.api.ConversationDeletionResult
import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.domain.ConversationStore
import kotlinx.coroutines.CancellationException

internal class ConversationDeletionCoordinator(
    private val store: ConversationStore?,
    private val repository: InMemoryRunRepository,
) {
    suspend fun delete(conversationId: ConversationId): ConversationDeletionResult =
        when (repository.updateDeletion(conversationId, DeletionOperation.Reserve)) {
            DeletionDecision.ConversationBusy -> ConversationDeletionResult.CONVERSATION_BUSY
            DeletionDecision.Closed -> ConversationDeletionResult.LIFECYCLE_CLOSED
            DeletionDecision.Reserved -> deleteReserved(conversationId)
            DeletionDecision.Released -> error("Deletion cannot be released before reservation")
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun deleteReserved(conversationId: ConversationId): ConversationDeletionResult {
        var discardRuns = false
        return try {
            val availableStore = store ?: return ConversationDeletionResult.STORAGE_UNAVAILABLE
            val result = if (availableStore.delete(conversationId)) {
                ConversationDeletionResult.DELETED
            } else {
                ConversationDeletionResult.NOT_FOUND
            }
            discardRuns = true
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ConversationDeletionResult.STORAGE_UNAVAILABLE
        } finally {
            repository.updateDeletion(conversationId, DeletionOperation.Release(discardRuns))
        }
    }
}
