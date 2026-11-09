package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId
import io.github.artemidius.blowhard.core.api.RunFailure
import io.github.artemidius.blowhard.core.api.RunFailureKind
import io.github.artemidius.blowhard.core.api.RunId
import io.github.artemidius.blowhard.core.api.RunSnapshot
import io.github.artemidius.blowhard.core.api.RunTimestamp
import kotlin.test.Test
import kotlin.test.assertFalse

class PresentedSnapshotTest {
    @Test
    fun `every failure category maps to copy without its reference`() {
        RunFailureKind.entries.forEach { kind ->
            val snapshot = RunSnapshot.Failed(
                runId = RunId("run"),
                conversationId = ConversationId("conversation"),
                updatedAt = RunTimestamp(1L),
                partialOutput = "partial",
                failure = RunFailure(kind, "private-reference"),
            )

            assertFalse(snapshot.present().notice!!.message.contains("private-reference"))
        }
    }
}
