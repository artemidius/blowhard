package io.github.artemidius.blowhard.ui.presentation

import io.github.artemidius.blowhard.core.api.ConversationId

/** Composition-root port that associates an offline scenario with a conversation. */
public fun interface DemoScenarioSelection {
    /** Prepares [scenario] before the run in [conversationId] is started. */
    public fun prepare(conversationId: ConversationId, scenario: DemoScenario)
}
