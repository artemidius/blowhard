package io.github.artemidius.blowhard.adapters.mistral

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformMistralHttpClient(): HttpClient = HttpClient(Darwin) {
    configureMistralClient()
}
