package io.github.artemidius.blowhard.adapters.mistral

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createPlatformMistralHttpClient(): HttpClient = HttpClient(CIO) {
    configureMistralClient()
}
